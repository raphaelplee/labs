-- Spring Modulith 2.0.x additional columns for event_publication
ALTER TABLE transflow.event_publication
    ADD COLUMN IF NOT EXISTS last_resubmission_date TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status                 TEXT;
