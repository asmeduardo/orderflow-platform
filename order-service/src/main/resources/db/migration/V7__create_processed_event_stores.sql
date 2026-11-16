-- Consumers record the id of every event they handle in the same transaction as its effects.
-- A redelivered event hits the primary key and is skipped; a rolled-back handling leaves no row,
-- so the event is processed again. Each module keeps its own store in its own schema.
create table orders.processed_event (
    event_id     uuid         primary key,
    event_type   varchar(100) not null,
    processed_at timestamptz  not null
);

create table inventory.processed_event (
    event_id     uuid         primary key,
    event_type   varchar(100) not null,
    processed_at timestamptz  not null
);
