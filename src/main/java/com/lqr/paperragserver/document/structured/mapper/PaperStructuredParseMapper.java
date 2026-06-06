package com.lqr.paperragserver.document.structured.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.document.structured.entity.PaperStructuredParseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 论文结构化解析结果数据访问接口。
 */
@Mapper
public interface PaperStructuredParseMapper extends BaseMapper<PaperStructuredParseEntity> {

    @Update("""
            insert into public.paper_structured_parse (
                id, owner_user_id, document_id, source_id, raw_text,
                rule_result, model_result, merged_result, field_confidence,
                missing_fields, low_confidence_fields, raw_model_output,
                status, error_message, parsed_at, created_at, updated_at
            ) values (
                #{id}, #{ownerUserId}, #{documentId}, #{sourceId}, #{rawText,jdbcType=LONGVARCHAR},
                cast(#{ruleResultJson} as jsonb), cast(#{modelResultJson} as jsonb), cast(#{mergedResultJson} as jsonb), cast(#{fieldConfidenceJson} as jsonb),
                cast(#{missingFieldsJson} as jsonb), cast(#{lowConfidenceFieldsJson} as jsonb), #{rawModelOutput,jdbcType=LONGVARCHAR},
                #{status}, #{errorMessage,jdbcType=LONGVARCHAR}, now(), now(), now()
            )
            on conflict (owner_user_id, source_id) do update set
                document_id = excluded.document_id,
                raw_text = excluded.raw_text,
                rule_result = excluded.rule_result,
                model_result = excluded.model_result,
                merged_result = excluded.merged_result,
                field_confidence = excluded.field_confidence,
                missing_fields = excluded.missing_fields,
                low_confidence_fields = excluded.low_confidence_fields,
                raw_model_output = excluded.raw_model_output,
                status = excluded.status,
                error_message = excluded.error_message,
                parsed_at = now(),
                updated_at = now()
            """)
    int upsertResult(@Param("id") UUID id,
                     @Param("ownerUserId") UUID ownerUserId,
                     @Param("documentId") UUID documentId,
                     @Param("sourceId") String sourceId,
                     @Param("rawText") String rawText,
                     @Param("ruleResultJson") String ruleResultJson,
                     @Param("modelResultJson") String modelResultJson,
                     @Param("mergedResultJson") String mergedResultJson,
                     @Param("fieldConfidenceJson") String fieldConfidenceJson,
                     @Param("missingFieldsJson") String missingFieldsJson,
                     @Param("lowConfidenceFieldsJson") String lowConfidenceFieldsJson,
                     @Param("rawModelOutput") String rawModelOutput,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage);

    @Update("""
            insert into public.paper_structured_parse (
                id, owner_user_id, document_id, source_id, raw_text,
                rule_result, model_result, merged_result, field_confidence,
                missing_fields, low_confidence_fields,
                status, error_message, created_at, updated_at
            ) values (
                #{id}, #{ownerUserId}, #{documentId}, #{sourceId}, #{rawText,jdbcType=LONGVARCHAR},
                '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
                '[]'::jsonb, '[]'::jsonb,
                'FAILED', #{errorMessage,jdbcType=LONGVARCHAR}, now(), now()
            )
            on conflict (owner_user_id, source_id) do update set
                document_id = excluded.document_id,
                raw_text = excluded.raw_text,
                status = 'FAILED',
                error_message = excluded.error_message,
                updated_at = now()
            """)
    int upsertFailed(@Param("id") UUID id,
                     @Param("ownerUserId") UUID ownerUserId,
                     @Param("documentId") UUID documentId,
                     @Param("sourceId") String sourceId,
                     @Param("rawText") String rawText,
                     @Param("errorMessage") String errorMessage);
}