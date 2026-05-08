-- PostgreSQL 扩展初始化脚本。
-- pgvector 用于存储向量；uuid-ossp 用于生成 UUID。

create extension if not exists vector;
create extension if not exists "uuid-ossp";