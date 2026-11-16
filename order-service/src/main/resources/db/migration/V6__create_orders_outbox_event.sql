-- Transactional outbox: events are inserted in the same transaction as the change they describe
-- and published to Kafka afterwards by a relay, so neither can happen without the other.
create table orders.outbox_event (
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
    constraint uk_outbox_event_position unique (position),
    constraint ck_outbox_event_attempts_not_negative check (attempts >= 0)
);

-- The relay only ever reads unpublished rows, in insertion order.
create index ix_outbox_event_pending on orders.outbox_event (position) where published_at is null;
