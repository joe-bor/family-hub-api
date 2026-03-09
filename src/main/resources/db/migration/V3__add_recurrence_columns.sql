ALTER TABLE calendar_event
    ADD COLUMN recurrence_rule    VARCHAR(255),
    ADD COLUMN recurring_event_id UUID REFERENCES calendar_event(id) ON DELETE CASCADE,
    ADD COLUMN original_date      DATE,
    ADD COLUMN is_cancelled       BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_calendar_event_recurring_event_id
    ON calendar_event(recurring_event_id);
