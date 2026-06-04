package com.lqr.paperragserver.vector.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

public interface VectorStoreMapper {

    /**
     * 新增或覆盖向量库记录。
     */
    @Insert("""
            insert into public.vector_store (id, content, metadata, embedding)
            values (#{id}, #{content}, cast(#{metadataJson} as json), cast(#{embeddingLiteral} as vector))
            on conflict (id) do update set
                content = excluded.content,
                metadata = excluded.metadata,
                embedding = excluded.embedding
            """)
    int upsert(@Param("id") UUID id,
               @Param("content") String content,
               @Param("metadataJson") String metadataJson,
               @Param("embeddingLiteral") String embeddingLiteral);

    /**
     * 删除指定用户的指定文档来源对应的全部向量记录。
     */
    @Delete("""
            delete from public.vector_store
            where metadata ->> 'ownerUserId' = #{ownerUserId}
              and metadata ->> 'sourceId' = #{sourceId}
            """)
    int deleteBySourceId(@Param("ownerUserId") String ownerUserId, @Param("sourceId") String sourceId);

    /**
     * 删除指定用户知识库文档来源对应的向量记录。
     */
    @Delete("""
            delete from public.vector_store
            where metadata ->> 'ownerUserId' = #{ownerUserId}
              and metadata ->> 'sourceId' = #{sourceId}
              and coalesce(metadata ->> 'sourceType', 'USER') = 'USER'
            """)
    int deleteUserVectorsBySourceId(@Param("ownerUserId") String ownerUserId, @Param("sourceId") String sourceId);

    /**
     * 删除指定用户的全部向量记录。
     */
    @Delete("""
            delete from public.vector_store
            where metadata ->> 'ownerUserId' = #{ownerUserId}
            """)
    int deleteByOwnerUserId(@Param("ownerUserId") String ownerUserId);

    /**
     * 删除指定用户的用户知识库向量记录。
     */
    @Delete("""
            delete from public.vector_store
            where metadata ->> 'ownerUserId' = #{ownerUserId}
              and coalesce(metadata ->> 'sourceType', 'USER') = 'USER'
            """)
    int deleteUserVectorsByOwnerUserId(@Param("ownerUserId") String ownerUserId);
}