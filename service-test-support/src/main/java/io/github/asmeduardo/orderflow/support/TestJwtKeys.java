package io.github.asmeduardo.orderflow.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * An RSA key created for the test run only, so no private key is ever committed. Its public half is
 * served as a JWKS document from a local HTTP endpoint, and the resource server is pointed at it.
 * Tokens are therefore validated exactly as in production (signature, issuer, audience and expiry)
 * without any identity provider running.
 */
public final class TestJwtKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String ISSUER = "http://localhost:9000";
    public static final String AUDIENCE = "orderflow-api";

    private static final String KEY_ID = "test-key";
    private static final RSAKey KEY = generate();
    private static final String JWK_SET_URI = serveJwkSet(new JWKSet(KEY.toPublicJWK()));

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + JWK_SET_URI,
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER);
    }

    public static String sign(JWTClaimsSet claims) {
        return sign(claims, KEY);
    }

    /** Signs with a key the application does not trust, announcing the trusted key id. */
    public static String signWithUnknownKey(JWTClaimsSet claims) {
        return sign(claims, generate());
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) {
        try {
            var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build();
            var jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String serveJwkSet(JWKSet jwkSet) {
        try {
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            var body = jwkSet.toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/oauth2/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return "http://localhost:" + server.getAddress().getPort() + "/oauth2/jwks";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
