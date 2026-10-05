-- Run as a PostgreSQL administrator. Do not commit the real password.
CREATE ROLE lakhdatar_monitor LOGIN PASSWORD 'REPLACE_WITH_RANDOM_PASSWORD';
GRANT CONNECT ON DATABASE lakhdatar TO lakhdatar_monitor;
GRANT pg_monitor TO lakhdatar_monitor;
