package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.claude.ClaudeUsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What Claude cost per day and per feature (TASK-67). Admin only (SecurityConfig). There is no admin screen for it yet. */
@RestController
@RequestMapping("/api/admin/usage")
public class AdminUsageController {

    private final ClaudeUsageService usage;

    public AdminUsageController(ClaudeUsageService usage) {
        this.usage = usage;
    }

    /** {@code days} counts back from today in UTC (default 7, at most 90). Newest day first. */
    @GetMapping
    public ClaudeUsageService.Report usage(@RequestParam(defaultValue = "7") int days) {
        return usage.report(days);
    }
}
