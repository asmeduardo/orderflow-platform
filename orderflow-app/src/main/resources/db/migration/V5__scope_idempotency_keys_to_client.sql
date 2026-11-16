-- Idempotency keys become unique per client (the subject of the access token) instead of
-- globally, so one client can never receive an order created by another.
alter table orders.idempotency_key add column client_id varchar(100);

-- Keys recorded before the API identified its clients belong to no real client. "#" cannot appear
-- in a client id issued by the authorization server, so these rows can never be matched again.
update orders.idempotency_key set client_id = '#unidentified' where client_id is null;

alter table orders.idempotency_key alter column client_id set not null;
alter table orders.idempotency_key drop constraint idempotency_key_pkey;
alter table orders.idempotency_key add constraint pk_idempotency_key primary key (client_id, idempotency_key);
