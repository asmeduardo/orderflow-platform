package io.github.asmeduardo.orderflow.shared.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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
        // Once a handler has a constrained parameter, Spring validates every parameter here,
        // including @Valid request bodies, whose errors then arrive as ParameterErrors.
        var errors = new ArrayList<Map<String, String>>();
        for (var result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors
                        .getFieldErrors()
                        .forEach(error -> errors.add(fieldError(error.getField(), error.getDefaultMessage())));
            } else {
                result.getResolvableErrors()
                        .forEach(error ->
                                errors.add(fieldError(nameOf(result.getMethodParameter()), error.getDefaultMessage())));
            }
        }
        errors.sort(Comparator.comparing(error -> error.get("field")));
        return ResponseEntity.badRequest().body(invalidRequest(errors));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception instanceof MissingRequestHeaderException missing) {
            return ResponseEntity.badRequest()
                    .body(invalidRequest(List.of(fieldError(missing.getHeaderName(), "is required"))));
        }
        return ResponseEntity.badRequest().body(invalidRequest(List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var field = exception.getPropertyName() == null ? "request" : exception.getPropertyName();
        return ResponseEntity.badRequest().body(invalidRequest(List.of(fieldError(field, "has an invalid format"))));
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

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized() {
        return Problems.of(
                HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "A valid bearer access token is required.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden() {
        return Problems.of(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                "The access token does not grant the scope this operation requires.");
    }

    @ExceptionHandler
    ProblemDetail concurrentModification(OptimisticLockingFailureException exception) {
        log.info("Request lost an optimistic locking race: {}", exception.getMessage());
        return Problems.of(
                HttpStatus.CONFLICT,
                "concurrent-modification",
                "Concurrent modification",
                "The resource was changed by another request. Fetch it again and retry if still needed.");
    }

    @ExceptionHandler
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected error while handling request", exception);
        return Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error", "An unexpected error occurred.");
    }

    private static ProblemDetail invalidRequest(List<Map<String, String>> errors) {
        var problem = Problems.of(
                HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Reports headers and query parameters by the name the client sent, not the Java name. */
    private static String nameOf(MethodParameter parameter) {
        var header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        var param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        return parameter.getParameterName();
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message == null ? "is invalid" : message);
    }
}
