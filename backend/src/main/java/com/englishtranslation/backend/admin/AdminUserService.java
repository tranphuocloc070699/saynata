package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.auth.AuthService;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The admin Users page (TASK-105): app accounts with their sign-in methods, their Claude usage (claude_usage.user_id,
 * recorded since TASK-105), block and delete. Plain SQL on purpose: one query gives a page of users with their counts.
 * "Explanations" are the WORD and SENTENCE calls; the cost is every call the user caused. Days are UTC days, like the
 * daily budget.
 */
@Service
public class AdminUserService {

    static final int MAX_REASON = 300;

    /** One row of the list. {@code providers} are "google" and "apple"; an account without one signs in by email. */
    public record UserRow(Long id, String email, String displayName, Instant createdAt, Instant lastSeenAt,
            boolean blocked, boolean admin, List<String> providers, long explanationsToday, long explanationsTotal,
            BigDecimal costTotalUsd) {
    }

    public record Page(List<UserRow> rows, long total) {
    }

    /** The four boxes on top of the page. */
    public record Summary(long accounts, long activeToday, long explanationsToday, BigDecimal costTodayUsd,
            Instant usageSince) {
    }

    public record Usage(long calls, BigDecimal costUsd) {
    }

    public record DayCalls(LocalDate date, long calls) {
    }

    public record Identity(String provider, String email, Instant since) {
    }

    /** The drawer. {@code days} is the last 14 UTC days, oldest first, empty days included. */
    public record UserDetail(UserRow user, String blockedReason, Instant blockedAt, Usage today, Usage last7Days,
            Usage total, long wordsTotal, long sentencesTotal, List<DayCalls> days, List<Identity> identities,
            long likedCount, long devicesSignedIn) {
    }

    /** Filters of the list; null = any. {@code provider} is google, apple or email (no Google or Apple sign-in). */
    public record Filter(String q, Boolean blocked, Boolean admin, String provider) {
    }

    private static final String EXPLANATION = "c.feature in ('WORD', 'SENTENCE')";

    private static final Map<String, String> SORTS = Map.of(
            "lastSeenAt", "u.last_seen_at",
            "createdAt", "u.created_at",
            "email", "u.email",
            "explanationsTotal", "explanations_total",
            "id", "u.id");

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;
    private final AuthService auth;
    private final ClaudeUsageService usage;
    private final Clock clock;

    public AdminUserService(NamedParameterJdbcTemplate jdbc, UserRepository users, AuthService auth,
            ClaudeUsageService usage, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.auth = auth;
        this.usage = usage;
        this.clock = clock;
    }

    public Page list(Filter filter, int start, int end, String sort, String order) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", Timestamp.from(usage.startOfToday()));
        String where = where(filter, params);
        long total = jdbc.queryForObject("select count(*) from users u where " + where, params, Long.class);

        String column = SORTS.getOrDefault(sort, "u.last_seen_at");
        String direction = "ASC".equalsIgnoreCase(order) ? "asc" : "desc";
        int limit = Math.max(0, Math.min(end, start + 200) - Math.max(0, start));
        params.addValue("limit", limit).addValue("offset", Math.max(0, start));
        List<UserRow> rows = jdbc.query("""
                select u.id, u.email, u.display_name, u.created_at, u.last_seen_at, u.blocked_at,
                  (select count(*) from user_roles r where r.user_id = u.id and r.role = 'ADMIN') as admin_roles,
                  (select count(*) from claude_usage c where c.user_id = u.id and %1$s and c.created_at >= :today)
                    as explanations_today,
                  (select count(*) from claude_usage c where c.user_id = u.id and %1$s) as explanations_total,
                  (select coalesce(sum(c.cost_usd), 0) from claude_usage c where c.user_id = u.id) as cost_total
                from users u where %2$s
                order by %3$s %4$s nulls last, u.id desc
                limit :limit offset :offset
                """.formatted(EXPLANATION, where, column, direction), params,
                (rs, n) -> new UserRow(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_seen_at")),
                        rs.getTimestamp("blocked_at") != null, rs.getLong("admin_roles") > 0, new ArrayList<>(),
                        rs.getLong("explanations_today"), rs.getLong("explanations_total"),
                        rs.getBigDecimal("cost_total")));
        addProviders(rows);
        return new Page(rows, total);
    }

    public Summary summary() {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", Timestamp.from(usage.startOfToday()));
        long accounts = jdbc.queryForObject("select count(*) from users", params, Long.class);
        long active = jdbc.queryForObject("select count(*) from users where last_seen_at >= :today", params,
                Long.class);
        long explanations = jdbc.queryForObject("select count(*) from claude_usage c where c.user_id is not null and "
                + EXPLANATION + " and c.created_at >= :today", params, Long.class);
        BigDecimal cost = jdbc.queryForObject("select coalesce(sum(cost_usd), 0) from claude_usage "
                + "where user_id is not null and created_at >= :today", params, BigDecimal.class);
        // The first call with a user: per-user usage starts there (older calls were saved without one).
        Instant since = instant(jdbc.queryForObject("select min(created_at) from claude_usage where user_id is not null",
                params, Timestamp.class));
        return new Summary(accounts, active, explanations, cost, since);
    }

    public UserDetail detail(long id) {
        User user = find(id);
        Instant now = clock.instant();
        Instant today = usage.startOfToday();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id)
                .addValue("today", Timestamp.from(today))
                .addValue("week", Timestamp.from(today.minus(6, ChronoUnit.DAYS)))
                .addValue("from", Timestamp.from(today.minus(13, ChronoUnit.DAYS)))
                .addValue("now", Timestamp.from(now));

        UserRow row = rowOf(user);
        Usage todayUsage = usageSince(params, ":today");
        Usage weekUsage = usageSince(params, ":week");
        Usage totalUsage = usageSince(params, null);
        long words = jdbc.queryForObject("select count(*) from claude_usage c where c.user_id = :id "
                + "and c.feature = 'WORD'", params, Long.class);
        long sentences = jdbc.queryForObject("select count(*) from claude_usage c where c.user_id = :id "
                + "and c.feature = 'SENTENCE'", params, Long.class);

        Map<LocalDate, Long> perDay = new HashMap<>();
        jdbc.query("select c.created_at from claude_usage c where c.user_id = :id and " + EXPLANATION
                + " and c.created_at >= :from", params, rs -> {
                    LocalDate day = rs.getTimestamp(1).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                    perDay.merge(day, 1L, Long::sum);
                });
        List<DayCalls> days = new ArrayList<>();
        LocalDate first = today.atZone(ZoneOffset.UTC).toLocalDate().minusDays(13);
        for (int i = 0; i < 14; i++) {
            LocalDate day = first.plusDays(i);
            days.add(new DayCalls(day, perDay.getOrDefault(day, 0L)));
        }

        List<Identity> identities = jdbc.query("select provider, email, created_at from user_identity "
                + "where user_id = :id order by created_at", params,
                (rs, n) -> new Identity(rs.getString("provider").toLowerCase(), rs.getString("email"),
                        instant(rs.getTimestamp("created_at"))));
        long liked = jdbc.queryForObject("select count(*) from liked_item where user_id = :id", params, Long.class);
        long devices = jdbc.queryForObject("select count(*) from refresh_token where user_id = :id "
                + "and revoked_at is null and expires_at > :now", params, Long.class);
        return new UserDetail(row, user.getBlockedReason(), user.getBlockedAt(), todayUsage, weekUsage, totalUsage,
                words, sentences, days, identities, liked, devices);
    }

    public UserDetail block(long id, String reason) {
        User user = findChangeable(id);
        String clean = reason == null ? null : reason.strip();
        if (clean != null && clean.length() > MAX_REASON) {
            throw new InvalidAdminRequestException("The reason can have at most " + MAX_REASON + " characters.");
        }
        user.block(clean == null || clean.isEmpty() ? null : clean, clock.instant());
        users.save(user);
        return detail(id);
    }

    public UserDetail unblock(long id) {
        User user = find(id);
        user.unblock();
        users.save(user);
        return detail(id);
    }

    /** Same as "Delete account" in the app: the account, its sign-ins, refresh tokens and Liked items. */
    public void delete(long id) {
        findChangeable(id);
        auth.deleteAccount(id);
    }

    private User find(long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with id " + id + "."));
    }

    /** Admin accounts cannot be blocked or deleted from this page. */
    private User findChangeable(long id) {
        User user = find(id);
        if (user.isAdmin()) {
            throw new InvalidAdminRequestException("Admin accounts cannot be blocked or deleted here.");
        }
        return user;
    }

    private UserRow rowOf(User user) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", user.getId())
                .addValue("today", Timestamp.from(usage.startOfToday()));
        long today = jdbc.queryForObject("select count(*) from claude_usage c where c.user_id = :id and "
                + EXPLANATION + " and c.created_at >= :today", params, Long.class);
        long total = jdbc.queryForObject("select count(*) from claude_usage c where c.user_id = :id and "
                + EXPLANATION, params, Long.class);
        BigDecimal cost = jdbc.queryForObject("select coalesce(sum(cost_usd), 0) from claude_usage "
                + "where user_id = :id", params, BigDecimal.class);
        List<UserRow> rows = new ArrayList<>(List.of(new UserRow(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getCreatedAt(), user.getLastSeenAt(), user.isBlocked(), user.isAdmin(),
                new ArrayList<>(), today, total, cost)));
        addProviders(rows);
        return rows.get(0);
    }

    private Usage usageSince(MapSqlParameterSource params, String from) {
        String since = from == null ? "" : " and c.created_at >= " + from;
        long calls = jdbc.queryForObject("select count(*) from claude_usage c where c.user_id = :id and "
                + EXPLANATION + since, params, Long.class);
        BigDecimal cost = jdbc.queryForObject("select coalesce(sum(c.cost_usd), 0) from claude_usage c "
                + "where c.user_id = :id" + since, params, BigDecimal.class);
        return new Usage(calls, cost);
    }

    private String where(Filter filter, MapSqlParameterSource params) {
        List<String> parts = new ArrayList<>(List.of("1 = 1"));
        if (filter.q() != null && !filter.q().isBlank()) {
            params.addValue("q", AdminListParams.likePattern(filter.q()));
            parts.add("(lower(u.email) like :q escape '\\' or lower(u.display_name) like :q escape '\\')");
        }
        if (filter.blocked() != null) {
            parts.add(filter.blocked() ? "u.blocked_at is not null" : "u.blocked_at is null");
        }
        if (filter.admin() != null) {
            parts.add((filter.admin() ? "" : "not ")
                    + "exists (select 1 from user_roles r where r.user_id = u.id and r.role = 'ADMIN')");
        }
        if (filter.provider() != null) {
            switch (filter.provider()) {
                case "google", "apple" -> {
                    params.addValue("provider", filter.provider().toUpperCase());
                    parts.add("exists (select 1 from user_identity i where i.user_id = u.id and i.provider = :provider)");
                }
                case "email" -> parts.add("not exists (select 1 from user_identity i where i.user_id = u.id)");
                default -> throw new InvalidAdminRequestException("provider must be google, apple or email.");
            }
        }
        return String.join(" and ", parts);
    }

    private void addProviders(List<UserRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, UserRow> byId = new LinkedHashMap<>();
        rows.forEach(row -> byId.put(row.id(), row));
        jdbc.query("select user_id, provider from user_identity where user_id in (:ids) order by created_at",
                new MapSqlParameterSource("ids", byId.keySet()), rs -> {
                    UserRow row = byId.get(rs.getLong("user_id"));
                    String provider = rs.getString("provider").toLowerCase();
                    if (row != null && !row.providers().contains(provider)) {
                        row.providers().add(provider);
                    }
                });
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
