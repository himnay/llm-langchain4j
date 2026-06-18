-- Each module (llm-chat-agent, llm-audio, llm-image) gets its own logical database in the
-- shared Postgres instance, so each has an independent api_keys table / Flyway history.
-- llm-chat-agent uses the POSTGRES_DB default (spring_ai); the other two are created here.
-- Only runs the first time the postgres_data volume is initialized (docker-entrypoint-initdb.d
-- convention) — drop the volume to re-run against a fresh instance.
CREATE DATABASE spring_ai_audio;
CREATE DATABASE spring_ai_image;
