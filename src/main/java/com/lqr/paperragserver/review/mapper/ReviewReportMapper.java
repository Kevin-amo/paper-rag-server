package com.lqr.paperragserver.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import com.lqr.paperragserver.review.entity.ReviewReportEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

public interface ReviewReportMapper extends BaseMapper<ReviewReportEntity> {

    @Select("""
            select *
            from public.review_report
            where task_id = #{taskId}
            order by updated_at desc
            limit 1
            """)
    @Results(id = "reviewReportResultMap", value = {
            @Result(column = "paper_sections", property = "paperSections", typeHandler = JsonbTypeHandler.class),
            @Result(column = "scores", property = "scores", typeHandler = JsonbTypeHandler.class),
            @Result(column = "comments", property = "comments", typeHandler = JsonbTypeHandler.class),
            @Result(column = "risks", property = "risks", typeHandler = JsonbTypeHandler.class),
            @Result(column = "raw_model_output", property = "rawModelOutput", typeHandler = JsonbTypeHandler.class)
    })
    ReviewReportEntity selectLatestByTaskId(@Param("taskId") UUID taskId);
}