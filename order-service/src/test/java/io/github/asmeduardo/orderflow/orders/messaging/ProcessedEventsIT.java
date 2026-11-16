package io.github.asmeduardo.orderflow.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.asmeduardo.orderflow.shared.messaging.ProcessedEvents;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ProcessedEventsIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    private ProcessedEvents processed() {
        return new ProcessedEvents(jdbc, Clock.systemUTC(), "orders.processed_event");
    }

    @Test
    void anEventIsNewOnlyTheFirstTime() {
        var eventId = UUID.randomUUID();

        Boolean first = transactions.execute(status -> processed().markProcessed(eventId, "OrderPlaced"));
        Boolean second = transactions.execute(status -> processed().markProcessed(eventId, "OrderPlaced"));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void aRolledBackHandlingDoesNotCountAsProcessed() {
        var eventId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            processed().markProcessed(eventId, "OrderPlaced");
            status.setRollbackOnly();
        });

        Boolean again = transactions.execute(status -> processed().markProcessed(eventId, "OrderPlaced"));

        assertThat(again).isTrue();
    }

    @Test
    void mustRunInsideTheTransactionThatAppliesTheEffects() {
        assertThatIllegalStateException().isThrownBy(() -> processed().markProcessed(UUID.randomUUID(), "OrderPlaced"));
    }

    @Test
    void refusesATableNameThatIsNotSchemaQualified() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessedEvents(jdbc, Clock.systemUTC(), "processed_event; drop table x"));
    }
}
