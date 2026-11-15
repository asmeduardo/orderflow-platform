-- order_id deliberately has no foreign key: orders belong to another module (and, later, to
-- another service). The inventory module only keeps the identifier it was given.
create table inventory.stock_reservation (
    id          uuid        primary key,
    order_id    uuid        not null,
    product_id  bigint      not null references inventory.product (id),
    quantity    integer     not null,
    status      varchar(16) not null,
    created_at  timestamptz not null,
    released_at timestamptz,
    constraint uk_stock_reservation_order_product unique (order_id, product_id),
    constraint ck_stock_reservation_quantity_positive check (quantity > 0),
    constraint ck_stock_reservation_status check (status in ('RESERVED', 'RELEASED')),
    constraint ck_stock_reservation_released_at check ((status = 'RELEASED') = (released_at is not null))
);

create index ix_stock_reservation_product on inventory.stock_reservation (product_id);
