CREATE TABLE chore (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES family(id) ON DELETE CASCADE,
    assigned_to_member_id UUID NOT NULL REFERENCES family_member(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    due_date DATE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) DEFAULT now(),
    updated_at TIMESTAMP(6) DEFAULT now()
);
