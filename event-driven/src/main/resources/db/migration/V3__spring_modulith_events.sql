-- Spring Modulith JPA event publication table (spring-modulith-events-jpa 2.0.x)
CREATE TABLE transflow.event_publication (
    id                    UUID        NOT NULL,
    listener_id           TEXT        NOT NULL,
    event_type            TEXT        NOT NULL,
    serialized_event      TEXT        NOT NULL,
    publication_date      TIMESTAMPTZ NOT NULL,
    completion_date       TIMESTAMPTZ,
    completion_attempts   INTEGER,
    last_resubmission_date TIMESTAMPTZ,
    status                TEXT,
    PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date ON transflow.event_publication (completion_date);
