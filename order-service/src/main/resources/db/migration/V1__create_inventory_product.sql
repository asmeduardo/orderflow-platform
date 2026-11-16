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
