package com.saynata.backend.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that turns every exception into the same JSON shape (RFC 9457 Problem Details).
 * ResponseEntityExceptionHandler already handles Spring's own errors (wrong method, bad JSON, missing param...).
 * We add our ApiException, validation details, the unique-index race, and a last safety net.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record FieldProblem(String field, String message) {
    }

    /** Our own errors: the service decides status, code and message. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException e, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setProperty("code", e.code());
        return createResponseEntity(problem, new HttpHeaders(), e.status(), request);
    }

    /** @Valid failed on a request body. Say which field is wrong, for every field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), f.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are not valid.");
        problem.setProperty("code", "validation_failed");
        problem.setProperty("errors", errors);
        return createResponseEntity(problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * The unique index said no. Two requests passed the "exists?" check at the same moment (a race),
     * and the database stopped the second one. The first one wins; the second gets a clean 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException e, WebRequest request) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This data already exists or breaks a database rule.");
        problem.setProperty("code", "conflict");
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /** Last safety net. Log everything here, tell the client almost nothing (no stack trace, no SQL). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception e, WebRequest request) {
        log.error("Unexpected error", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
        problem.setProperty("code", "internal_error");
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Every error body (ours and Spring's) passes here, so every one gets the request id. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                problem.setProperty("requestId", requestId);
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
