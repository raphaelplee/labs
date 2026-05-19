-- Spring Modulith JPA event publication table (spring-modulith-events-jpa)
CREATE TABLE transflow.event_publication (
    id               UUID        NOT NULL,
    listener_id      TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    serialized_event TEXT        NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date  TIMESTAMPTZ,
    PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date ON transflow.event_publication (completion_date);
