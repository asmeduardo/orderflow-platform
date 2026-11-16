-- Outbox of the inventory module: reservation outcomes are published from here, never by writing
-- to another module's tables. Same structure and rules as orders.outbox_event.
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
