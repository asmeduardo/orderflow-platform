package io.github.asmeduardo.orderflow.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON files under {@code contracts/} are the published contracts. A change that breaks one of
 * these tests is a breaking change for consumers and needs a new event version instead.
 */
class EventContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID EVENT_ID = UUID.fromString("0195f2a4-7d1e-7c3a-9a55-3f1d2c4b5a69");
    private static final UUID ORDER_ID = UUID.fromString("5b0c8f3e-2d1a-4c7b-9e6f-8a7d6c5b4a32");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-15T12:00:00.123456Z");

    @Test
    void orderPlacedV1() throws Exception {
        var event = envelope(
                EventTypes.ORDER_PLACED,
                new OrderPlacedV1(ORDER_ID, List.of(new OrderPlacedV1.Line("MUG-BLUE-01", 2))));

        assertMatchesContract(event, "order-placed-v1.json");
        assertReadsContractIgnoringUnknownFields("order-placed-v1.json", OrderPlacedV1.class, event.payload());
    }

    @Test
    void orderCancelledV1() throws Exception {
        var event = envelope(EventTypes.ORDER_CANCELLED, new OrderCancelledV1(ORDER_ID));

        assertMatchesContract(event, "order-cancelled-v1.json");
        assertReadsContractIgnoringUnknownFields("order-cancelled-v1.json", OrderCancelledV1.class, event.payload());
    }

    @Test
    void stockReservedV1() throws Exception {
        var event = envelope(EventTypes.STOCK_RESERVED, new StockReservedV1(ORDER_ID));

        assertMatchesContract(event, "stock-reserved-v1.json");
        assertReadsContractIgnoringUnknownFields("stock-reserved-v1.json", StockReservedV1.class, event.payload());
    }

    @Test
    void stockRejectedV1() throws Exception {
        var event = envelope(
                EventTypes.STOCK_REJECTED,
                new StockRejectedV1(
                        ORDER_ID,
                        List.of(new StockRejectedV1.Shortage(
                                "LAMP-DESK-01", StockRejectedV1.Reason.INSUFFICIENT_STOCK, 2, 1))));

        assertMatchesContract(event, "stock-rejected-v1.json");
        assertReadsContractIgnoringUnknownFields("stock-rejected-v1.json", StockRejectedV1.class, event.payload());
    }

    @Test
    void envelopeExposesTheVersionedType() {
        var event = envelope(EventTypes.ORDER_CANCELLED, new OrderCancelledV1(ORDER_ID));

        assertThat(event.eventType()).isEqualTo("OrderCancelled");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(ORDER_ID.toString());
    }

    private static <T> EventEnvelope<T> envelope(EventType type, T payload) {
        return new EventEnvelope<>(EVENT_ID, type.name(), type.version(), OCCURRED_AT, ORDER_ID.toString(), payload);
    }

    private static void assertMatchesContract(EventEnvelope<?> event, String contract) throws Exception {
        JSONAssert.assertEquals(read(contract), JSON.writeValueAsString(event), JSONCompareMode.STRICT);
    }

    /** A newer producer may add fields; a v1 consumer must still read the event (tolerant reader). */
    private static <T> void assertReadsContractIgnoringUnknownFields(String contract, Class<T> type, T expected) {
        var tree = (ObjectNode) JSON.readTree(read(contract));
        ((ObjectNode) tree.get("payload")).put("fieldAddedLater", "ignored");
        tree.put("envelopeFieldAddedLater", true);

        var payload = JSON.treeToValue(tree.get("payload"), type);

        assertThat(payload).isEqualTo(expected);
        assertThat(JSON.treeToValue(tree, EventEnvelope.class).eventId()).isEqualTo(EVENT_ID);
    }

    private static String read(String contract) {
        try (var in = EventContractTest.class.getResourceAsStream("/contracts/" + contract)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
