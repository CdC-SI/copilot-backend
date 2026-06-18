FROM pgvector/pgvector:pg18

COPY pg-textsearch-postgresql-18_1.2.0-1_amd64.deb /tmp/pg_textsearch.deb
RUN dpkg -i /tmp/pg_textsearch.deb \
    && rm /tmp/pg_textsearch.deb

RUN echo "shared_preload_libraries = 'pg_textsearch'" >> /usr/share/postgresql/postgresql.conf.sample
COPY init-extensions.sql /docker-entrypoint-initdb.d/
