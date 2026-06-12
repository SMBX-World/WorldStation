create extension if not exists pg_trgm;

create index if not exists title_index
    on maps using gin (title_lower gin_trgm_ops);
