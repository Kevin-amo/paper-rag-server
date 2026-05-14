package com.lqr.paperragserver.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.paper.entity.PaperDocumentChunkEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

public interface PaperDocumentChunkMapper extends BaseMapper<PaperDocumentChunkEntity> {

    /**
     * 查询所有可参与关键词检索的有效文档分块。
     */
    @Select("""
            select c.chunk_id, c.source_id, c.chunk_index, c.content, c.metadata
            from public.paper_document_chunk c
            join public.paper_document d on d.source_id = c.source_id
            where d.deleted_at is null
              and d.status = 'INDEXED'
            order by c.source_id asc, c.chunk_index asc
            """)
    List<PaperDocumentChunkEntity> selectSearchCandidates();

    /**
     * 回写文档分块关联的向量库记录 ID。
     */
    @Update("""
            update public.paper_document_chunk
            set vector_store_id = #{vectorStoreId}, updated_at = now()
            where chunk_id = #{chunkId}
            """)
    int updateVectorStoreId(@Param("chunkId") String chunkId, @Param("vectorStoreId") UUID vectorStoreId);
}