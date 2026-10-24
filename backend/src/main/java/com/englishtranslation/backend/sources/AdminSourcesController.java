package com.englishtranslation.backend.sources;

import com.englishtranslation.backend.admin.InvalidAdminRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin API for the Sources page (TASK-108, doc-13): upload a book, read its candidates, queue the picked ones.
 * ADMIN only (SecurityConfig), no LLM call. The upload is a multipart form and needs the header X-Requested-With
 * (JsonWritesOnlyFilter).
 */
@RestController
@RequestMapping("/api/admin/sources")
public class AdminSourcesController {

    /** The body of POST /{id}/queue. */
    public record QueueRequest(List<SourceService.Pick> items) {
    }

    private final SourceService service;

    public AdminSourcesController(SourceService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SourceService.View upload(@RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAdminRequestException("Choose a .txt or .md file.");
        }
        try {
            return service.upload(title, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/{id}")
    public SourceService.View get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/queue")
    public SourceService.QueueResult queue(@PathVariable long id, @RequestBody QueueRequest request) {
        return service.queue(id, request.items());
    }
}
