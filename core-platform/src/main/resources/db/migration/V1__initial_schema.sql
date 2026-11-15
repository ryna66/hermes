CREATE TABLE organization (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(255) NOT NULL,
    organization_id UUID REFERENCES organization(id),
    active BOOLEAN NOT NULL
);

CREATE TABLE faculty_profile (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    user_id UUID UNIQUE REFERENCES app_user(id),
    name VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    bio TEXT NOT NULL,
    email VARCHAR(255) NOT NULL
);

CREATE TABLE faculty_expertise (faculty_profile_id UUID NOT NULL REFERENCES faculty_profile(id), tag VARCHAR(255) NOT NULL, PRIMARY KEY (faculty_profile_id, tag));

CREATE TABLE challenge (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_by_id UUID NOT NULL REFERENCES app_user(id),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    category VARCHAR(255),
    suggested_category VARCHAR(255),
    classification_confidence DOUBLE PRECISION,
    embedding_text TEXT,
    embedding_model VARCHAR(255),
    duplicate_of_id UUID REFERENCES challenge(id),
    status VARCHAR(255) NOT NULL,
    moderation_required BOOLEAN NOT NULL,
    moderation_reason TEXT,
    assigned_university_id UUID REFERENCES organization(id),
    assigned_at TIMESTAMP WITH TIME ZONE,
    response_deadline TIMESTAMP WITH TIME ZONE,
    assignment_response VARCHAR(255),
    version BIGINT NOT NULL
);

CREATE TABLE challenge_event (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    challenge_id UUID NOT NULL REFERENCES challenge(id),
    from_state VARCHAR(255),
    to_state VARCHAR(255) NOT NULL,
    actor_id UUID NOT NULL REFERENCES app_user(id),
    reason TEXT
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    challenge_id UUID NOT NULL UNIQUE REFERENCES challenge(id),
    university_id UUID NOT NULL REFERENCES organization(id),
    title VARCHAR(255) NOT NULL,
    proposal_text TEXT NOT NULL,
    impact_summary TEXT,
    version BIGINT NOT NULL
);

CREATE TABLE team (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    project_id UUID NOT NULL UNIQUE REFERENCES project(id),
    name VARCHAR(255) NOT NULL
);

CREATE TABLE team_member (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    team_id UUID NOT NULL REFERENCES team(id),
    faculty_profile_id UUID NOT NULL REFERENCES faculty_profile(id),
    role VARCHAR(255) NOT NULL,
    CONSTRAINT uq_team_faculty UNIQUE (team_id, faculty_profile_id)
);

CREATE TABLE milestone (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    project_id UUID NOT NULL REFERENCES project(id),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    outcome TEXT
);

CREATE TABLE pledge (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    project_id UUID NOT NULL REFERENCES project(id),
    industry_organization_id UUID NOT NULL REFERENCES organization(id),
    created_by_id UUID NOT NULL REFERENCES app_user(id),
    support_type VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2),
    currency VARCHAR(255),
    description TEXT NOT NULL,
    CONSTRAINT ck_funding CHECK (support_type <> 'FUNDING' OR (amount IS NOT NULL AND amount > 0 AND currency IS NOT NULL))
);

CREATE TABLE notification (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient_id UUID NOT NULL REFERENCES app_user(id),
    challenge_id UUID REFERENCES challenge(id),
    challenge_event_id UUID REFERENCES challenge_event(id),
    message TEXT NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE
);
