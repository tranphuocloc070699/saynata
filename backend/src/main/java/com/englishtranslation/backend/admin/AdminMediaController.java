package com.englishtranslation.backend.admin;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Admin API for the picture of a sense (TASK-52). Like all of /api/admin it needs the ADMIN role (SecurityConfig).
 * A sense is named by the word id ("b-12" or "w-45") and its position (0 = first sense).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMediaController {

    private final AdminMediaService service;

    public AdminMediaController(AdminMediaService service) {
        this.service = service;
    }

    /** ?sense=b-12:0 and optional ?q=search words. Rate limited. */
    @GetMapping("/media/search")
    public AdminMediaService.SearchResponse search(@RequestParam("sense") String sense,
            @RequestParam(name = "q", required = false) String q, Authentication admin) {
        int separator = sense.lastIndexOf(':');
        if (separator < 0) {
            throw new InvalidAdminRequestException("sense must look like b-12:0 (word id, colon, sense position).");
        }
        return service.search(sense.substring(0, separator), position(sense.substring(separator + 1)), q, admin.getName());
    }

    /** The senses marked for an AI image: ?status=needs_ai. */
    @GetMapping("/media")
    public ResponseEntity<List<AdminMediaService.MarkedSense>> list(@RequestParam("status") String status) {
        List<AdminMediaService.MarkedSense> marked = service.listByStatus(status);
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(marked.size())).body(marked);
    }

    /** Body: the result chosen from the search. Returns the changed word. */
    @PutMapping("/words/{id}/senses/{position}/media")
    public AdminWord replace(@PathVariable String id, @PathVariable int position,
            @RequestBody AdminMediaService.ChosenResult chosen) {
        return service.replace(id, position, chosen);
    }

    @DeleteMapping("/words/{id}/senses/{position}/media")
    public AdminWord clear(@PathVariable String id, @PathVariable int position) {
        return service.clear(id, position);
    }

    /** No media for any sense of the word (TASK-76). Returns the changed word. */
    @DeleteMapping("/words/{id}/media")
    public AdminWord removeAll(@PathVariable String id) {
        return service.removeAllMedia(id);
    }

    /**
     * The admin's own picture or clip (TASK-79). Multipart form with one part named "file". The browser must send the
     * header X-Requested-With (JsonWritesOnlyFilter lets this one path skip the JSON rule only with that header).
     * Returns the changed word.
     */
    @PostMapping(path = "/words/{id}/senses/{position}/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminWord upload(@PathVariable String id, @PathVariable int position,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return service.upload(id, position, file);
    }

    @PostMapping("/words/{id}/senses/{position}/media/mark-for-ai")
    public AdminWord markForAi(@PathVariable String id, @PathVariable int position) {
        return service.markForAi(id, position);
    }

    private static int position(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sense " + text + ".");
        }
    }
}
