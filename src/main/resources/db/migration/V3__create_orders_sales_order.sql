create table orders.sales_order (
    id               uuid          primary key,
    channel          varchar(32)   not null,
    currency         varchar(3)    not null,
    total_amount     numeric(19,4) not null,
    status           varchar(16)   not null,
    rejection_reason varchar(500),
    version          bigint        not null,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    constraint ck_sales_order_currency check (currency ~ '^[A-Z]{3}$'),
    constraint ck_sales_order_total_not_negative check (total_amount >= 0),
    constraint ck_sales_order_status check (status in ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    constraint ck_sales_order_rejection_reason check ((status = 'REJECTED') = (rejection_reason is not null))
);

create index ix_sales_order_status_created_at on orders.sales_order (status, created_at desc);

-- sku is a plain value: the orders module never joins with inventory tables.
create table orders.sales_order_item (
    id          bigint generated always as identity primary key,
    order_id    uuid          not null references orders.sales_order (id),
    line_number integer       not null,
    sku         varchar(64)   not null,
    quantity    integer       not null,
    unit_price  numeric(19,4) not null,
    constraint uk_sales_order_item_line unique (order_id, line_number),
    constraint uk_sales_order_item_sku unique (order_id, sku),
    constraint ck_sales_order_item_quantity_positive check (quantity > 0),
    constraint ck_sales_order_item_unit_price_not_negative check (unit_price >= 0)
);
