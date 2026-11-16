-- Initial schema of the inventory database. It consolidates the inventory migrations of the modular
-- monolith (order-service V1, V2, V7, V8 and V9), so the tables keep their structure and the data
-- can be copied over as is during the cut-over described in ADR 0009.

create table inventory.product (
    id                bigint generated always as identity primary key,
    sku               varchar(64)  not null,
    name              varchar(200) not null,
    on_hand_quantity  integer      not null,
    reserved_quantity integer      not null default 0,
    version           bigint       not null default 0,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    constraint uk_product_sku unique (sku),
    constraint ck_product_on_hand_not_negative check (on_hand_quantity >= 0),
    constraint ck_product_reserved_within_on_hand check (reserved_quantity between 0 and on_hand_quantity)
);

-- order_id has no foreign key: orders live in another service. The inventory only keeps the
-- identifier it received in the event.
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

-- Orders known to be cancelled, so a late OrderPlaced never reserves stock.
create table inventory.cancelled_order (
    order_id     uuid        primary key,
    cancelled_at timestamptz not null
);

-- Events consumed, recorded in the same transaction as their effects.
create table inventory.processed_event (
    event_id     uuid         primary key,
    event_type   varchar(100) not null,
    processed_at timestamptz  not null
);

-- Events to publish, written in the same transaction as the change they describe.
create table inventory.outbox_event (
    id            uuid          primary key,
    position      bigint        generated always as identity,
    topic         varchar(200)  not null,
    message_key   varchar(200)  not null,
    event_type    varchar(100)  not null,
    event_version integer       not null,
    payload       jsonb         not null,
    created_at    timestamptz   not null,
    published_at  timestamptz,
    attempts      integer       not null default 0,
    last_error    varchar(1000),
    constraint uk_inventory_outbox_event_position unique (position),
    constraint ck_inventory_outbox_event_attempts_not_negative check (attempts >= 0)
);

create index ix_inventory_outbox_event_pending on inventory.outbox_event (position) where published_at is null;
