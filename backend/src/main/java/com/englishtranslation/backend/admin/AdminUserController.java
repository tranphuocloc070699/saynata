package com.englishtranslation.backend.admin;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for app accounts (TASK-105): list, one account with its usage, block, unblock, delete. ADMIN only.
 * Same list shape as words: {@code _start}, {@code _end}, {@code _sort}, {@code _order}, X-Total-Count.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    /** The body of POST /{id}/block; the reason is optional and only admins see it. */
    public record BlockRequest(String reason) {
    }

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserService.UserRow>> list(
            @RequestParam(name = "_start", defaultValue = "0") int start,
            @RequestParam(name = "_end", defaultValue = "50") int end,
            @RequestParam(name = "_sort", defaultValue = "lastSeenAt") String sort,
            @RequestParam(name = "_order", defaultValue = "DESC") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "blocked", required = false) Boolean blocked,
            @RequestParam(name = "admin", required = false) Boolean admin,
            @RequestParam(name = "provider", required = false) String provider) {
        AdminUserService.Page page = service.list(new AdminUserService.Filter(q, blocked, admin, provider), start, end,
                sort, order);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.total()))
                .body(page.rows());
    }

    /** The four totals on top of the page. */
    @GetMapping("/summary")
    public AdminUserService.Summary summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public AdminUserService.UserDetail get(@PathVariable long id) {
        return service.detail(id);
    }

    /** No new Claude call for this account until it is unblocked; cached and bundled answers still work. */
    @PostMapping("/{id}/block")
    public AdminUserService.UserDetail block(@PathVariable long id, @RequestBody(required = false) BlockRequest body) {
        return service.block(id, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/unblock")
    public AdminUserService.UserDetail unblock(@PathVariable long id) {
        return service.unblock(id);
    }

    /** Same as "Delete account" in the app. Cannot be undone. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
