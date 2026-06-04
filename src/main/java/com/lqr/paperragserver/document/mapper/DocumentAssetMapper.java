package com.lqr.paperragserver.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.document.entity.DocumentAssetEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * 文档派生资源数据访问接口。
 */
public interface DocumentAssetMapper extends BaseMapper<DocumentAssetEntity> {

    @Delete("""
            delete from public.paper_document_asset a
            using public.paper_document d
            where a.owner_user_id = #{ownerUserId}
              and d.owner_user_id = a.owner_user_id
              and d.source_id = a.source_id
              and coalesce(d.metadata ->> 'sourceType', 'USER') = 'USER'
            """)
    int deleteUserDocumentAssets(@Param("ownerUserId") UUID ownerUserId);
}