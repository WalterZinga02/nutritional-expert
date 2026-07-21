CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE availability_slot (
    id BIGSERIAL PRIMARY KEY,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_availability_slot_start UNIQUE (start_time),
    CONSTRAINT ck_availability_slot_time CHECK (end_time > start_time)
);

CREATE TABLE appointment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    slot_id BIGINT NOT NULL UNIQUE REFERENCES availability_slot(id),
    type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_appointment_type CHECK (
        type IN ('WEIGHING_AND_BMI', 'DIET_CREATION', 'DIET_REVIEW', 'NUTRITIONAL_CONSULTATION')
    ),
    CONSTRAINT ck_appointment_status CHECK (
        status IN ('REQUESTED', 'CONFIRMED', 'COMPLETED', 'CANCELLED')
    )
);

CREATE INDEX ix_appointment_user ON appointment(user_id);
CREATE INDEX ix_appointment_status ON appointment(status);

CREATE TABLE review (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointment(id),
    rating SMALLINT NOT NULL,
    comment VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5)
);

