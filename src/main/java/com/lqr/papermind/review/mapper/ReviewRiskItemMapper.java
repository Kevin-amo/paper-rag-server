package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import com.lqr.papermind.review.entity.ReviewRiskItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface ReviewRiskItemMapper extends BaseMapper<ReviewRiskItemEntity> {

    /**
     * 根据评审报告ID删除所有风险项
     *
     * @param reportId 评审报告ID
     * @return 删除的记录数
     */
    @Delete("delete from public.review_risk_item where report_id = #{reportId}")
    int deleteByReportId(@Param("reportId") UUID reportId);

    /**
     * 根据评审报告ID查询所有风险项
     *
     * @param reportId 评审报告ID
     * @return 风险项列表
     */
    @Select("select * from public.review_risk_item where report_id = #{reportId} order by created_at asc")
    @Results(id = "reviewRiskItemResultMap", value = {
            @Result(column = "evidence_location", property = "evidenceLocation", typeHandler = JsonbTypeHandler.class)
    })
    List<ReviewRiskItemEntity> selectByReportId(@Param("reportId") UUID reportId);
}
