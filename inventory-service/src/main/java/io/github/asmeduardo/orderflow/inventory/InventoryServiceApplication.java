package io.github.asmeduardo.orderflow.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Products and stock. Reacts to order events and publishes the outcome of each reservation. */
@SpringBootApplication(
        scanBasePackages = {"io.github.asmeduardo.orderflow.inventory", "io.github.asmeduardo.orderflow.shared"})
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
