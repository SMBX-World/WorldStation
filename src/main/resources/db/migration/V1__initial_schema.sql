create table if not exists maps
(
    id                serial,
    title             varchar(72) not null,
    title_lower       varchar(72) not null,
    author            varchar(32),
    uploader          integer     not null,
    game_version      integer     not null,
    download_provider integer     not null,
    download_url      varchar(1024)
);

create index if not exists maps_author_index
    on maps (author);

create index if not exists maps_game_version_index
    on maps (game_version);

create unique index if not exists maps_id_uindex
    on maps (id);

create index if not exists maps_uploader_index
    on maps (uploader);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'maps_pk'
    ) then
        alter table maps
            add constraint maps_pk
                primary key (id);
    end if;
end $$;

create table if not exists motd
(
    id      serial primary key,
    content text    not null,
    enabled boolean not null default true
);
