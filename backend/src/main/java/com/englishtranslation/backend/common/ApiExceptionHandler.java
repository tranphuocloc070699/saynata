package com.englishtranslation.backend.common;

import com.anthropic.errors.AnthropicServiceException;
import com.englishtranslation.backend.claude.ClaudeRefusedException;
import com.englishtranslation.backend.claude.NotEnglishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps validation errors to a clear 400, and Claude errors to 422 or 502. */
// Order -1: Spring's own problem-details advice (spring.mvc.problemdetails.enabled) has order 0
// and would answer first with "Invalid request content.".
@Order(-1)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Spring's default detail is "Invalid request content.", which tells the user nothing.
     * Say what is wrong with the field instead (TASK-22).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String field = error == null ? "" : error.getField();
        String detail = switch (field) {
            case "text" -> "Text must be 1-500 characters.";
            case "targetLang" -> "Choose a target language.";
            default -> "Invalid request.";
        };
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

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
