ALTER TABLE transflow.fulfillment_records
    ADD CONSTRAINT uq_fulfillment_records_order_id UNIQUE (order_id);

DROP INDEX transflow.idx_fulfillment_order_id;
