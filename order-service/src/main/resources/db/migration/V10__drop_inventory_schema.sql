-- The inventory context moved to inventory-service, which owns its own database (ADR 0009). Its
-- tables left this database: copy their data to the inventory database before running this
-- migration, following the cut-over steps in the ADR. Nothing in the orders schema refers to them.
drop schema inventory cascade;
