package io.github.asmeduardo.orderflow.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DatabaseSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void theOrdersDatabaseHoldsOnlyTheOrdersSchemaAfterTheInventoryExtraction() {
        var schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata where schema_name in ('orders', 'inventory')",
                String.class);

        assertThat(schemas).containsExactly("orders");
    }
}
