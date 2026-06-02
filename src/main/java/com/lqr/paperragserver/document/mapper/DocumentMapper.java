package com.lqr.paperragserver.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.document.entity.DocumentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 文档主表数据访问接口，负责文档基础信息、元数据和状态更新。
 */
public interface DocumentMapper extends BaseMapper<DocumentEntity> {

    /**
     * 新增或覆盖文档解析中的基础信息。
     */
    @Update("""
            insert into public.paper_document (
                owner_user_id, source_id, title, origin, file_name, file_type, file_size,
                authors, abstract, doi, journal, publish_year, keywords,
                content_text, metadata, status, chunk_count, error_message
            ) values (
                #{ownerUserId}, #{sourceId}, #{title}, #{origin,jdbcType=VARCHAR}, #{fileName,jdbcType=VARCHAR}, #{fileType,jdbcType=VARCHAR}, #{fileSize,jdbcType=BIGINT},
                cast(#{authorsJson,jdbcType=VARCHAR} as jsonb), #{abstractText,jdbcType=LONGVARCHAR}, #{doi,jdbcType=VARCHAR}, #{journal,jdbcType=VARCHAR}, #{publishYear,jdbcType=INTEGER}, cast(#{keywordsJson,jdbcType=VARCHAR} as jsonb),
                #{contentText,jdbcType=LONGVARCHAR}, cast(#{metadataJson} as jsonb), 'PARSING', 0, null
            )
            on conflict (owner_user_id, source_id) do update set
                title = excluded.title,
                origin = excluded.origin,
                file_name = excluded.file_name,
                file_type = excluded.file_type,
                file_size = excluded.file_size,
                authors = excluded.authors,
                abstract = excluded.abstract,
                doi = excluded.doi,
                journal = excluded.journal,
                publish_year = excluded.publish_year,
                keywords = excluded.keywords,
                content_text = excluded.content_text,
                metadata = excluded.metadata,
                status = 'PARSING',
                chunk_count = 0,
                error_message = null,
                deleted_at = null,
                updated_at = now()
            """)
    int upsertParsing(@Param("ownerUserId") UUID ownerUserId,
                      @Param("sourceId") String sourceId,
                      @Param("title") String title,
                      @Param("origin") String origin,
                      @Param("fileName") String fileName,
                      @Param("fileType") String fileType,
                      @Param("fileSize") Long fileSize,
                      @Param("authorsJson") String authorsJson,
                      @Param("abstractText") String abstractText,
                      @Param("doi") String doi,
                      @Param("journal") String journal,
                      @Param("publishYear") Integer publishYear,
                      @Param("keywordsJson") String keywordsJson,
                      @Param("contentText") String contentText,
                      @Param("metadataJson") String metadataJson);

    /**
     * 更新文档的可编辑元数据字段。
     */
    @Update("""
            update public.paper_document
            set title = coalesce(#{title,jdbcType=VARCHAR}, title),
                authors = coalesce(cast(#{authorsJson,jdbcType=VARCHAR} as jsonb), authors),
                abstract = coalesce(#{abstractText,jdbcType=LONGVARCHAR}, abstract),
                doi = coalesce(#{doi,jdbcType=VARCHAR}, doi),
                journal = coalesce(#{journal,jdbcType=VARCHAR}, journal),
                publish_year = coalesce(#{publishYear,jdbcType=INTEGER}, publish_year),
                keywords = coalesce(cast(#{keywordsJson,jdbcType=VARCHAR} as jsonb), keywords),
                metadata = metadata || cast(#{metadataJson} as jsonb),
                updated_at = now()
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
            """)
    int updateMetadata(@Param("ownerUserId") UUID ownerUserId,
                       @Param("sourceId") String sourceId,
                       @Param("title") String title,
                       @Param("authorsJson") String authorsJson,
                       @Param("abstractText") String abstractText,
                       @Param("doi") String doi,
                       @Param("journal") String journal,
                       @Param("publishYear") Integer publishYear,
                       @Param("keywordsJson") String keywordsJson,
                       @Param("metadataJson") String metadataJson);

    /**
     * 恢复已软删除文档。
     */
    @Update("""
            update public.paper_document
            set status = 'PENDING', deleted_at = null, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
              and deleted_at is not null
            """)
    int restore(@Param("ownerUserId") UUID ownerUserId, @Param("sourceId") String sourceId);

    /**
     * 标记文档索引完成并记录分块数量。
     */
    @Update("""
            update public.paper_document
            set status = 'INDEXED', chunk_count = #{chunkCount}, error_message = null, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
              and deleted_at is null
              and status <> 'DELETED'
            """)
    int markIndexed(@Param("ownerUserId") UUID ownerUserId,
                    @Param("sourceId") String sourceId,
                    @Param("chunkCount") int chunkCount);

    /**
     * 标记文档进入指定处理状态。
     */
    @Update("""
            update public.paper_document
            set status = #{status}, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
              and deleted_at is null
              and status <> 'DELETED'
            """)
    int markStatus(@Param("ownerUserId") UUID ownerUserId,
                   @Param("sourceId") String sourceId,
                   @Param("status") String status,
                   @Param("progress") int progress);

    /**
     * 标记文档处理失败并保存错误信息。
     */
    @Update("""
            update public.paper_document
            set status = 'FAILED', error_message = #{errorMessage,jdbcType=LONGVARCHAR}, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
            """)
    int markFailed(@Param("ownerUserId") UUID ownerUserId,
                   @Param("sourceId") String sourceId,
                   @Param("errorMessage") String errorMessage);

    /**
     * 软删除指定文档。
     */
    @Update("""
            update public.paper_document
            set status = 'DELETED', deleted_at = now(), updated_at = now()
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
            """)
    int markDeleted(@Param("ownerUserId") UUID ownerUserId, @Param("sourceId") String sourceId);

    /**
     * 软删除当前用户的全部有效文档。
     */
    @Update("""
            update public.paper_document
            set status = 'DELETED', deleted_at = now(), updated_at = now()
            where owner_user_id = #{ownerUserId}
              and deleted_at is null
              and status <> 'DELETED'
            """)
    int markAllDeleted(@Param("ownerUserId") UUID ownerUserId);

    /**
     * 统计指定来源 ID 的有效文档数量。
     */
    @Select("""
            select count(*)
            from public.paper_document
            where owner_user_id = #{ownerUserId}
              and deleted_at is null
              and status <> 'DELETED'
              and source_id = #{sourceId}
            """)
    long countActiveBySourceId(@Param("ownerUserId") UUID ownerUserId, @Param("sourceId") String sourceId);
}