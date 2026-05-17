-- Migrate subscription_id columns from VARCHAR to UUID
ALTER TABLE transflow.orders
    ALTER COLUMN subscription_id TYPE UUID USING subscription_id::uuid;

ALTER TABLE transflow.fulfillment_records
    ALTER COLUMN subscription_id TYPE UUID USING subscription_id::uuid;
