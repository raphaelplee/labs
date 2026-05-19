-- Spring Modulith 2.0.x added completion_attempts to event_publication
ALTER TABLE transflow.event_publication
    ADD COLUMN completion_attempts INTEGER;
