package com.lqr.paperragserver.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import com.lqr.paperragserver.review.entity.ReviewConsensusEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

public interface ReviewConsensusMapper extends BaseMapper<ReviewConsensusEntity> {

    @Select("""
            select *
            from public.review_consensus
            where task_id = #{taskId}
            limit 1
            """)
    @Results(id = "reviewConsensusResultMap", value = {
            @Result(column = "score_summary", property = "scoreSummary", typeHandler = JsonbTypeHandler.class),
            @Result(column = "comment_summary", property = "commentSummary", typeHandler = JsonbTypeHandler.class),
            @Result(column = "disagreement_items", property = "disagreementItems", typeHandler = JsonbTypeHandler.class)
    })
    ReviewConsensusEntity selectByTaskId(@Param("taskId") UUID taskId);
}
