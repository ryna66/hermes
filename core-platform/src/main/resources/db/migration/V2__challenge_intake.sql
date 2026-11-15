ALTER TABLE challenge ADD COLUMN district VARCHAR(255) DEFAULT 'Unknown' NOT NULL;
ALTER TABLE challenge ADD COLUMN affected_people INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE challenge ADD COLUMN photo_path TEXT;
ALTER TABLE challenge ADD COLUMN audio_path TEXT;
ALTER TABLE challenge ADD COLUMN fixed_confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE challenge ADD CONSTRAINT ck_affected_people CHECK (affected_people >= 1);
