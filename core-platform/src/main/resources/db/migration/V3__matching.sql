ALTER TABLE challenge ADD COLUMN duplicate_candidate boolean DEFAULT false NOT NULL;
ALTER TABLE challenge ADD COLUMN reassignment_required boolean DEFAULT false NOT NULL;
CREATE TABLE assignment_record (
    id uuid PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    challenge_id uuid NOT NULL REFERENCES challenge(id),
    university_id uuid NOT NULL REFERENCES organization(id),
    response varchar(32) NOT NULL
);
