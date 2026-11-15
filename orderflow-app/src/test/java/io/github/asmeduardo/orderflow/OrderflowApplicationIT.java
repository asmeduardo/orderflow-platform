package io.github.asmeduardo.orderflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class OrderflowApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsCreateOneSchemaPerModule() {
        var schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata where schema_name in ('orders', 'inventory')",
                String.class);

        assertThat(schemas).containsExactlyInAnyOrder("orders", "inventory");
    }
}
