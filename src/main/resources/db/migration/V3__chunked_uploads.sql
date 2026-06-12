create table if not exists upload_sessions
(
    id                     uuid primary key,
    user_id                integer      not null,
    upload_kind            varchar(32)  not null,
    file_name              text         not null,
    content_type           text         not null,
    total_size             bigint       not null,
    chunk_size             integer      not null,
    total_chunks           integer      not null,
    target_path            text         not null,
    temp_dir               text         not null,
    status                 varchar(32)  not null,
    saved_chunk_count      integer      not null default 0,
    final_url              text,
    error                  text,
    created_at_epoch_ms    bigint       not null,
    updated_at_epoch_ms    bigint       not null,
    expires_at_epoch_ms    bigint       not null
);

create table if not exists upload_chunks
(
    upload_id              uuid         not null references upload_sessions(id) on delete cascade,
    chunk_index            integer      not null,
    size                   integer      not null,
    sha256                 varchar(64)  not null,
    received_at_epoch_ms   bigint       not null,
    saved_at_epoch_ms      bigint,
    primary key (upload_id, chunk_index)
);

create index if not exists idx_upload_sessions_user_status
    on upload_sessions(user_id, status);

create index if not exists idx_upload_sessions_expires_at
    on upload_sessions(expires_at_epoch_ms);
