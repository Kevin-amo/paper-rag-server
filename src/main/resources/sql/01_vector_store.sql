-- Spring AI pgvector 向量表。
-- 表名需要和 application.yaml 中的 spring.ai.vectorstore.pgvector.table-name 保持一致。
-- 维度固定为 DashScope text-embedding-v4 使用的 1536，避免运行时向量维度不匹配。

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store
(
    id       uuid NOT NULL DEFAULT uuid_generate_v4(),
    content  text,
    metadata json,
    embedding public.vector(1536),
    CONSTRAINT vector_store_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON public.vector_store
    USING hnsw (embedding public.vector_cosine_ops);

create index if not exists vector_store_owner_user_id_idx
    on public.vector_store ((metadata ->> 'ownerUserId'));

CREATE INDEX IF NOT EXISTS vector_store_source_id_idx
    ON public.vector_store ((metadata ->> 'sourceId'));

CREATE INDEX IF NOT EXISTS vector_store_chunk_id_idx
    ON public.vector_store ((metadata ->> 'chunkId'));