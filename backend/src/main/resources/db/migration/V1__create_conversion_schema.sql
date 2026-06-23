create table conversion_jobs (
    id uuid primary key,
    version bigint not null default 0,
    original_filename varchar(255) not null,
    safe_original_filename varchar(255) not null,
    source_file_path text not null,
    result_file_path text,
    file_extension varchar(8) not null,
    declared_mime_type varchar(128),
    detected_mime_type varchar(128) not null,
    file_size_bytes bigint not null,
    checksum_sha256 char(64) not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    max_attempts integer not null default 3,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    processing_started_at timestamp with time zone,
    processing_finished_at timestamp with time zone,
    next_attempt_at timestamp with time zone,
    expires_at timestamp with time zone not null,
    expired_at timestamp with time zone,
    cleanup_completed_at timestamp with time zone,
    last_error_code varchar(64),
    last_error_message text,

    constraint chk_conversion_jobs_status
        check (status in ('CREATED', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    constraint chk_conversion_jobs_last_error_code
        check (
            last_error_code is null
            or last_error_code in (
                'CORRUPTED_DOCUMENT',
                'CONVERTER_UNAVAILABLE',
                'CONVERTER_TIMEOUT',
                'PROCESSING_INTERRUPTED',
                'STORAGE_ERROR',
                'INTERNAL_ERROR'
            )
        ),
    constraint chk_conversion_jobs_file_size_positive
        check (file_size_bytes > 0),
    constraint chk_conversion_jobs_attempt_count
        check (attempt_count >= 0 and attempt_count <= max_attempts),
    constraint chk_conversion_jobs_max_attempts_positive
        check (max_attempts > 0),
    constraint chk_conversion_jobs_sha256_format
        check (checksum_sha256 ~ '^[0-9a-fA-F]{64}$'),
    constraint chk_conversion_jobs_result
        check (status <> 'COMPLETED' or result_file_path is not null),
    constraint chk_conversion_jobs_expired_at
        check (
            (status = 'EXPIRED' and expired_at is not null)
            or (status <> 'EXPIRED' and expired_at is null)
        ),
    constraint chk_conversion_jobs_cleanup_completed_at
        check (
            cleanup_completed_at is null
            or (
                status = 'EXPIRED'
                and expired_at is not null
                and cleanup_completed_at >= expired_at
            )
        ),
    constraint chk_conversion_jobs_next_attempt_at
        check (
            (status = 'CREATED' and next_attempt_at is not null)
            or (status <> 'CREATED' and next_attempt_at is null)
        )
);

create table conversion_attempts (
    id uuid primary key,
    job_id uuid not null,
    attempt_number integer not null,
    status varchar(32) not null,
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone,
    duration_ms bigint,
    external_request_id varchar(128),
    error_code varchar(64),
    error_message text,

    constraint fk_conversion_attempts_job
        foreign key (job_id)
        references conversion_jobs (id)
        on delete cascade,
    constraint uq_conversion_attempts_job_attempt_number
        unique (job_id, attempt_number),
    constraint chk_conversion_attempts_status
        check (status in ('STARTED', 'SUCCEEDED', 'FAILED')),
    constraint chk_conversion_attempts_error_code
        check (
            error_code is null
            or error_code in (
                'CORRUPTED_DOCUMENT',
                'CONVERTER_UNAVAILABLE',
                'CONVERTER_TIMEOUT',
                'PROCESSING_INTERRUPTED',
                'STORAGE_ERROR',
                'INTERNAL_ERROR'
            )
        ),
    constraint chk_conversion_attempts_attempt_number_positive
        check (attempt_number > 0),
    constraint chk_conversion_attempts_duration_non_negative
        check (duration_ms is null or duration_ms >= 0),
    constraint chk_conversion_attempts_completion
        check (
            (status = 'STARTED' and finished_at is null and duration_ms is null and error_code is null)
            or (
                status = 'SUCCEEDED'
                and finished_at is not null
                and duration_ms is not null
                and error_code is null
            )
            or (
                status = 'FAILED'
                and finished_at is not null
                and duration_ms is not null
                and error_code is not null
            )
        )
);

create index idx_conversion_jobs_ready_for_processing
    on conversion_jobs (next_attempt_at, created_at)
    where status = 'CREATED';

create index idx_conversion_jobs_stalled_processing
    on conversion_jobs (processing_started_at)
    where status = 'PROCESSING';

create index idx_conversion_jobs_cleanup_pending
    on conversion_jobs (expires_at, status)
    where cleanup_completed_at is null;

create index idx_conversion_attempts_started_recovery
    on conversion_attempts (job_id, started_at)
    where status = 'STARTED';
