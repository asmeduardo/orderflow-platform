package io.github.asmeduardo.orderflow.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details whose {@code type} is a stable, documented identifier. */
public final class Problems {

    private static final String TYPE_PREFIX = "urn:orderflow:problem:";

    private Problems() {}

    public static ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setTitle(title);
        return problem;
    }
}
