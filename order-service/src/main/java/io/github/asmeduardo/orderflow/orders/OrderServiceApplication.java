package io.github.asmeduardo.orderflow.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Order intake API. Talks to the inventory service only through Kafka events. */
@SpringBootApplication(
        scanBasePackages = {"io.github.asmeduardo.orderflow.orders", "io.github.asmeduardo.orderflow.shared"})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
