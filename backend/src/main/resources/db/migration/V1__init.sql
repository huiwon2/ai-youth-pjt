CREATE TABLE planning_sessions (
    id uuid PRIMARY KEY,
    access_token_hash text NOT NULL UNIQUE,
    assessment_date date NOT NULL,
    revision integer NOT NULL DEFAULT 0 CHECK (revision >= 0),
    priority_preset text NOT NULL
        CHECK (priority_preset IN ('BALANCED', 'COST', 'COMMUTE', 'NEIGHBORHOOD', 'CUSTOM')),
    score_weights jsonb NOT NULL,
    facility_preferences jsonb NOT NULL CHECK (jsonb_typeof(facility_preferences) = 'array'),
    accessibility_preferences jsonb NOT NULL CHECK (jsonb_typeof(accessibility_preferences) = 'object'),
    expires_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT planning_sessions_score_weights_check CHECK (
        CASE WHEN jsonb_typeof(score_weights) = 'object'
            AND jsonb_typeof(score_weights -> 'cost') = 'number'
            AND jsonb_typeof(score_weights -> 'commute') = 'number'
            AND jsonb_typeof(score_weights -> 'convenience') = 'number'
        THEN (score_weights ->> 'cost')::numeric BETWEEN 0 AND 100
            AND (score_weights ->> 'commute')::numeric BETWEEN 0 AND 100
            AND (score_weights ->> 'convenience')::numeric BETWEEN 0 AND 100
            AND (score_weights ->> 'cost')::numeric
                + (score_weights ->> 'commute')::numeric
                + (score_weights ->> 'convenience')::numeric = 100
        ELSE false END
    )
);

CREATE TABLE profiles (
    session_id uuid PRIMARY KEY REFERENCES planning_sessions (id) ON DELETE CASCADE,
    display_name text,
    age_years integer CHECK (age_years >= 0),
    age_as_of_date date,
    employment_status text CHECK (employment_status IN ('EMPLOYED', 'STUDENT', 'JOB_SEEKER', 'OTHER')),
    available_cash_won bigint CHECK (available_cash_won >= 0),
    monthly_income_won bigint CHECK (monthly_income_won >= 0),
    is_homeless boolean,
    lives_with_parents boolean,
    household_status text,
    max_commute_minutes integer CHECK (max_commute_minutes >= 0),
    desired_move_in_month text CHECK (desired_move_in_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    additional_answers jsonb CHECK (jsonb_typeof(additional_answers) = 'object'),
    -- An incomplete profile can be saved before confirmation.
    confirmed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT profiles_age_date_check CHECK ((age_years IS NULL) = (age_as_of_date IS NULL))
);

CREATE TABLE frequent_places (
    session_id uuid PRIMARY KEY REFERENCES planning_sessions (id) ON DELETE CASCADE,
    name text NOT NULL,
    confirmed_address text NOT NULL,
    confirmed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE candidate_homes (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES planning_sessions (id) ON DELETE CASCADE,
    slot_no integer NOT NULL CHECK (slot_no BETWEEN 1 AND 3),
    label text NOT NULL,
    confirmed_address text NOT NULL,
    -- The API accepts a candidate whose housing type is still unknown.
    housing_type text CHECK (housing_type IN ('JEONSE', 'MONTHLY', 'PUBLIC_RENTAL')),
    deposit_won bigint NOT NULL CHECK (deposit_won >= 0),
    monthly_rent_won bigint NOT NULL CHECK (monthly_rent_won >= 0),
    maintenance_won bigint CHECK (maintenance_won >= 0),
    exclusive_area_m2 numeric CHECK (exclusive_area_m2 > 0),
    input_method text NOT NULL CHECK (input_method = 'MANUAL'),
    field_provenance jsonb NOT NULL CHECK (jsonb_typeof(field_provenance) = 'object'),
    confirmed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT candidate_homes_session_slot_key UNIQUE (session_id, slot_no),
    CONSTRAINT candidate_homes_jeonse_rent_check CHECK (housing_type <> 'JEONSE' OR monthly_rent_won = 0)
);

-- Keep the column names and IDs supplied by policy_catalog.v3.
CREATE TABLE policies (
    id uuid PRIMARY KEY,
    policy_key text NOT NULL UNIQUE,
    source text NOT NULL CHECK (source IN ('GOV24', 'LH', 'YOUTHCENTER')),
    external_id text NOT NULL,
    name text NOT NULL,
    review_decision text NOT NULL CHECK (review_decision IN ('INCLUDE', 'REVIEW', 'EXCLUDE')),
    organization text,
    service_field text,
    summary text,
    benefit jsonb NOT NULL CHECK (jsonb_typeof(benefit) = 'object'),
    application jsonb NOT NULL CHECK (jsonb_typeof(application) = 'object'),
    eligibility jsonb NOT NULL CHECK (jsonb_typeof(eligibility) = 'object'),
    source_url text,
    source_modified_at_raw text,
    CONSTRAINT policies_source_external_id_key UNIQUE (source, external_id)
);

CREATE TABLE rag_chunks (
    id uuid PRIMARY KEY,
    policy_id uuid REFERENCES policies (id) ON DELETE SET NULL,
    source_key text NOT NULL,
    document_key text NOT NULL,
    document_revision text NOT NULL,
    chunk_index integer NOT NULL CHECK (chunk_index >= 0),
    section text NOT NULL,
    content text NOT NULL,
    source_url text,
    locator text,
    content_hash text NOT NULL,
    verification_status text NOT NULL CHECK (verification_status IN ('PENDING', 'VERIFIED')),
    is_active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT rag_chunks_document_revision_chunk_key UNIQUE (document_key, document_revision, chunk_index)
);

CREATE INDEX rag_chunks_policy_id_idx ON rag_chunks (policy_id);

CREATE TABLE comparison_runs (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES planning_sessions (id) ON DELETE CASCADE,
    input_revision integer NOT NULL CHECK (input_revision >= 0),
    assessment_date date NOT NULL,
    calculation_version text NOT NULL,
    status text NOT NULL CHECK (status IN ('COMPLETE', 'PARTIAL')),
    input_snapshot jsonb NOT NULL CHECK (jsonb_typeof(input_snapshot) = 'object'),
    policy_snapshot jsonb NOT NULL CHECK (jsonb_typeof(policy_snapshot) = 'array'),
    result_snapshot jsonb NOT NULL CHECK (jsonb_typeof(result_snapshot) = 'object'),
    calculated_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX comparison_runs_session_calculated_at_idx ON comparison_runs (session_id, calculated_at DESC);
