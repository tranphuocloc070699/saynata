package com.englishtranslation.backend.aipicture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for the AI picture of a sense (TASK-91). Like all of /api/admin it needs the ADMIN role (SecurityConfig).
 * A sense is named by the word id ("b-12" or "w-45") and its position (0 = first sense).
 */
@RestController
@RequestMapping("/api/admin/words/{id}/senses/{position}/media")
public class AiPictureController {

    /** @param source where the prompt came from (scene, template, claude); anything else is saved as "edited" */
    public record DrawRequest(String prompt, String source) {
    }

    private final AiPromptService prompts;
    private final AiPictureService pictures;

    public AiPictureController(AiPromptService prompts, AiPictureService pictures) {
        this.prompts = prompts;
        this.pictures = pictures;
    }

    /** The prompt to show in the dialog: the newest media-ai scene, else the template. Free. */
    @GetMapping("/ai-prompt")
    public AiPromptService.PromptAnswer prompt(@PathVariable String id, @PathVariable int position) {
        return prompts.prompt(id, position);
    }

    /** The wand: Claude Sonnet writes a new scene (about $0.02). */
    @PostMapping("/ai-prompt/scene")
    public AiPromptService.PromptAnswer newScene(@PathVariable String id, @PathVariable int position) {
        return prompts.newScene(id, position);
    }

    /** OK in the dialog: starts the background job. 202 with the job. */
    @PostMapping("/ai-picture")
    public ResponseEntity<AiPictureService.Job> draw(@PathVariable String id, @PathVariable int position,
            @RequestBody DrawRequest request, Authentication admin) {
        AiPictureService.Job job = pictures.start(id, position, request.prompt(), request.source(), admin.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /** The newest job of the sense, or 204 when there is none. The admin page asks this every 3 seconds. */
    @GetMapping("/ai-picture")
    public ResponseEntity<AiPictureService.Job> latest(@PathVariable String id, @PathVariable int position) {
        return pictures.latest(id, position).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
