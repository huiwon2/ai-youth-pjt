ALTER TABLE frequent_places ADD COLUMN location_details jsonb;
ALTER TABLE frequent_places ADD COLUMN destination_version uuid;

ALTER TABLE candidate_homes ADD COLUMN location_details jsonb;
ALTER TABLE candidate_homes ADD COLUMN lookup_details jsonb;
ALTER TABLE candidate_homes ADD COLUMN route_destination_version uuid;

CREATE TABLE location_choices (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES planning_sessions (id) ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('DESTINATION', 'HOME')),
    details jsonb NOT NULL CHECK (jsonb_typeof(details) = 'object'),
    expires_at timestamptz NOT NULL
);
CREATE INDEX location_choices_session_expiry_idx ON location_choices (session_id, expires_at);

CREATE TABLE home_previews (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES planning_sessions (id) ON DELETE CASCADE,
    destination_version uuid NOT NULL,
    details jsonb NOT NULL CHECK (jsonb_typeof(details) = 'object'),
    expires_at timestamptz NOT NULL
);
CREATE INDEX home_previews_session_expiry_idx ON home_previews (session_id, expires_at);
