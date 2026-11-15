package io.github.asmeduardo.orderflow.orders.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * SHA-256 of a canonical form of the request, used to tell a retry from a different request that
 * reuses an idempotency key. Item order and trailing zeros in prices do not change the meaning of
 * a request, so they do not change the fingerprint either.
 */
final class OrderRequestFingerprint {

    private OrderRequestFingerprint() {}

    static String of(PlaceOrderCommand command) {
        var canonical = new StringBuilder()
                .append("channel=").append(command.channel()).append('\n')
                .append("currency=").append(command.currency()).append('\n');
        command.lines().stream()
                .sorted(Comparator.comparing(line -> line.sku()))
                .forEach(line -> canonical
                        .append("line=")
                        .append(line.sku()).append('|')
                        .append(line.quantity()).append('|')
                        .append(line.unitPrice().stripTrailingZeros().toPlainString())
                        .append('\n'));
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform must support SHA-256", e);
        }
    }
}
