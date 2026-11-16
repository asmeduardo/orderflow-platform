package io.github.asmeduardo.orderflow.shared.security;

/** OAuth 2.0 scopes understood by the API. */
public final class Scopes {

    /** Query orders, products and stock levels. */
    public static final String READ = "orderflow.read";

    /** Place and cancel orders, register products. */
    public static final String OPERATE = "orderflow.operate";

    private Scopes() {}

    /** Spring Security exposes each scope of the token as a {@code SCOPE_} authority. */
    static String authority(String scope) {
        return "SCOPE_" + scope;
    }
}
