-- One row per client request that created an order. The primary key serializes concurrent
-- retries; the fingerprint tells a retry from a different request reusing the key.
create table orders.idempotency_key (
    idempotency_key     varchar(100) primary key,
    request_fingerprint varchar(64)  not null,
    order_id            uuid         not null references orders.sales_order (id),
    created_at          timestamptz  not null,
    constraint uk_idempotency_key_order unique (order_id),
    constraint ck_idempotency_key_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$')
);
