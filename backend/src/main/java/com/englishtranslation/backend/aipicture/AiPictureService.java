package com.englishtranslation.backend.aipicture;

import com.englishtranslation.backend.admin.AdminMediaService;
import com.englishtranslation.backend.admin.AdminWordId;
import com.englishtranslation.backend.admin.InvalidAdminRequestException;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.media.MediaCleanup;
import com.englishtranslation.backend.media.MediaStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The AI picture jobs (TASK-91). The admin presses OK in the dialog: {@link #start} saves a job row (queued) and gives
 * it to ONE worker thread, so Codex runs one picture at a time and further jobs wait. The worker draws the picture
 * (ImageDrawer), saves it as a hashed WebP (MediaStore.saveAi) and links it to the sense in one transaction
 * (AdminMediaService.linkAiPicture). A failure keeps the old picture and saves a plain error in the job.
 * The admin page asks {@link #latest} every 3 seconds, also after the dialog is closed or the page is reopened,
 * because the state is in the database.
 */
@Service
public class AiPictureService {

    private static final Logger log = LoggerFactory.getLogger(AiPictureService.class);

    /** The most characters of a prompt (the dialog and the server both check). */
    public static final int MAX_PROMPT_LENGTH = 4000;
    static final String RESTARTED = "The server restarted.";

    /** A job as the admin page sees it. */
    public record Job(long id, String status, String error, Instant createdAt, Instant finishedAt) {
        static Job of(AiPictureJobs.Row row) {
            return new Job(row.id(), row.status(), row.error(), row.createdAt(), row.finishedAt());
        }
    }

    private final AiPictureProperties properties;
    private final AiPictureJobs jobs;
    private final AiPromptService prompts;
    private final ImageDrawer drawer;
    private final MediaStore store;
    private final MediaCleanup cleanup;
    private final AdminMediaService media;
    private final ClaudeUsageService claudeUsage;
    // One worker: Codex draws one picture at a time. Daemon, so a running job never keeps the JVM alive.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ai-picture");
        thread.setDaemon(true);
        return thread;
    });

    public AiPictureService(AiPictureProperties properties, AiPictureJobs jobs, AiPromptService prompts,
            ImageDrawer drawer, MediaStore store, MediaCleanup cleanup, AdminMediaService media,
            ClaudeUsageService claudeUsage) {
        this.properties = properties;
        this.jobs = jobs;
        this.prompts = prompts;
        this.drawer = drawer;
        this.store = store;
        this.cleanup = cleanup;
        this.media = media;
        this.claudeUsage = claudeUsage;
    }

    /** A job that was queued or drawing when the server stopped will never finish: mark it failed. */
    @EventListener(ApplicationReadyEvent.class)
    public void failUnfinishedJobs() {
        int failed = jobs.failUnfinished(RESTARTED);
        if (failed > 0) {
            log.warn("Marked {} AI picture job(s) as failed: the server restarted.", failed);
        }
    }

    @PreDestroy
    void stopWorker() {
        worker.shutdownNow();
    }

    /**
     * Saves a job and queues it. {@code promptSource} is scene, template, claude or edited (only for the record).
     * Errors: 503 ai_unavailable (Codex is not set up), 400 (empty or too long prompt), 404 (no such sense),
     * 409 ai_job_running (this sense has a job already), 429 ai_picture_limit (today's picture cap).
     */
    public synchronized Job start(String wordId, int position, String prompt, String promptSource, String admin) {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_unavailable",
                    "AI pictures are not set up on this server. Set AI_PICTURES_ENABLED=true and sign Codex in (doc-11).");
        }
        String text = prompt == null ? "" : prompt.strip();
        if (text.isEmpty()) {
            throw new InvalidAdminRequestException("Write a prompt first.");
        }
        if (text.length() > MAX_PROMPT_LENGTH) {
            throw new InvalidAdminRequestException("The prompt must be " + MAX_PROMPT_LENGTH + " characters or fewer.");
        }
        prompts.sense(wordId, position); // 404 when the word or the sense is missing
        String id = AdminWordId.parse(wordId).text();
        if (jobs.hasRunning(id, position)) {
            throw new ApiException(HttpStatus.CONFLICT, "ai_job_running",
                    "A picture for this sense is already being drawn. Wait until it is done.");
        }
        if (jobs.countSince(claudeUsage.startOfToday()) >= properties.picturesPerDay()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ai_picture_limit",
                    "Today's limit of " + properties.picturesPerDay() + " AI pictures is used up. Try again tomorrow.");
        }
        long jobId = jobs.insert(id, position, text, sourceOrEdited(promptSource), admin);
        worker.execute(() -> run(jobId));
        return jobs.find(jobId).map(Job::of).orElseThrow();
    }

    /** The newest job of the sense, whatever its status. Empty when the sense never had one. */
    public Optional<Job> latest(String wordId, int position) {
        prompts.sense(wordId, position);
        return jobs.latest(AdminWordId.parse(wordId).text(), position).map(Job::of);
    }

    /** Runs on the worker thread. Never throws: every problem ends as a failed job. */
    void run(long jobId) {
        Long newMediaId = null;
        try {
            AiPictureJobs.Row job = jobs.find(jobId).orElseThrow();
            jobs.markDrawing(jobId);
            AiPromptService.SenseView sense = prompts.sense(job.wordId(), job.position());
            byte[] png = drawer.draw(job.prompt());
            long mediaId = store.saveAi(sense.word(), job.position(), png);
            newMediaId = mediaId;
            media.linkAiPicture(job.wordId(), job.position(), mediaId, old -> jobs.markDone(jobId, mediaId, old));
            log.info("AI picture job {} done for {} sense {}", jobId, job.wordId(), job.position() + 1);
        } catch (DrawException e) {
            fail(jobId, e.getMessage());
        } catch (IOException | RuntimeException e) {
            log.warn("AI picture job {} failed: {}", jobId, e.toString(), e);
            if (newMediaId != null) {
                cleanup.deleteIfUnused(Set.of(newMediaId)); // saved but not linked
            }
            fail(jobId, "Could not save the picture. Try again.");
        }
    }

    private void fail(long jobId, String error) {
        log.warn("AI picture job {} failed: {}", jobId, error);
        try {
            jobs.markFailed(jobId, error);
        } catch (RuntimeException e) {
            log.error("Could not mark AI picture job {} as failed: {}", jobId, e.toString());
        }
    }

    private static String sourceOrEdited(String source) {
        return Set.of(AiPromptService.SCENE, AiPromptService.TEMPLATE, AiPromptService.CLAUDE).contains(source)
                ? source : "edited";
    }
}
