package io.github.asmeduardo.orderflow.shared.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates framework and unexpected failures into problem details. Module specific handlers run
 * first; this one only sees what they do not handle.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalProblemHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(invalidRequest(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(invalidRequest(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(Problems.of(
                        HttpStatus.BAD_REQUEST,
                        "malformed-request",
                        "Malformed request",
                        "The request body could not be read."));
    }

    @ExceptionHandler
    ProblemDetail dataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Request rejected by a database constraint", exception);
        return Problems.of(
                HttpStatus.CONFLICT,
                "conflict",
                "Conflicting state",
                "The request conflicts with the current state of the resource.");
    }

    @ExceptionHandler
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected error while handling request", exception);
        return Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error", "An unexpected error occurred.");
    }

    private static ProblemDetail invalidRequest(List<Map<String, String>> errors) {
        var problem =
                Problems.of(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message == null ? "is invalid" : message);
    }
}
