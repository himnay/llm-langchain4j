-- Marks which source PDFs have already been chunked/embedded into the Redis EmbeddingStore, so
-- DocumentIngestionRunner doesn't re-ingest (and duplicate) them on every application restart.
CREATE TABLE IF NOT EXISTS ingested_documents (
    file_name    VARCHAR(200) PRIMARY KEY,
    ingested_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
