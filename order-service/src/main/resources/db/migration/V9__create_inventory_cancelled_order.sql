-- Orders the inventory knows to be cancelled. An OrderPlaced that arrives after the cancellation
-- (for example replayed from the dead-letter topic) must not reserve stock that nobody will release.
create table inventory.cancelled_order (
    order_id     uuid        primary key,
    cancelled_at timestamptz not null
);
