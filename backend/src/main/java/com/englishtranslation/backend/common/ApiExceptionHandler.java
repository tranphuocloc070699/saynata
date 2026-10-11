package com.englishtranslation.backend.common;

import com.anthropic.errors.AnthropicServiceException;
import com.englishtranslation.backend.claude.ClaudeRefusedException;
import com.englishtranslation.backend.claude.NotEnglishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Validation errors (400) are handled by Spring itself. This class only maps Claude errors. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ClaudeRefusedException.class)
    public ProblemDetail handleRefused(ClaudeRefusedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(NotEnglishException.class)
    public ProblemDetail handleNotEnglish(NotEnglishException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(AnthropicServiceException.class)
    public ProblemDetail handleClaudeError(AnthropicServiceException e) {
        log.warn("Claude API error: status={} message={}", e.statusCode(), e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The translation service is not available. Try again later.");
    }
}
