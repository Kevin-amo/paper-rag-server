package com.lqr.papermind.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import com.lqr.papermind.review.entity.ReviewReportEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface ReviewReportMapper extends BaseMapper<ReviewReportEntity> {

    /**
     * 查询指定评审任务的最新评审报告
     *
     * @param taskId 评审任务ID
     * @return 最新的评审报告，不存在则返回null
     */
    @Select("""
            select *
            from public.review_report
            where task_id = #{taskId}
            order by updated_at desc
            limit 1
            """)
    @Results(id = "reviewReportResultMap", value = {
            @Result(column = "assignment_id", property = "assignmentId"),
            @Result(column = "paper_sections", property = "paperSections", typeHandler = JsonbTypeHandler.class),
            @Result(column = "scores", property = "scores", typeHandler = JsonbTypeHandler.class),
            @Result(column = "comments", property = "comments", typeHandler = JsonbTypeHandler.class),
            @Result(column = "risks", property = "risks", typeHandler = JsonbTypeHandler.class),
            @Result(column = "raw_model_output", property = "rawModelOutput", typeHandler = JsonbTypeHandler.class),
            @Result(column = "manual_delta", property = "manualDelta", typeHandler = JsonbTypeHandler.class)
    })
    ReviewReportEntity selectLatestByTaskId(@Param("taskId") UUID taskId);

    /**
     * 根据分配记录ID查询评审报告
     *
     * @param assignmentId 分配记录ID
     * @return 评审报告，不存在则返回null
     */
    @Select("""
            select *
            from public.review_report
            where assignment_id = #{assignmentId}
            order by updated_at desc
            limit 1
            """)
    @ResultMap("reviewReportResultMap")
    ReviewReportEntity selectByAssignmentId(@Param("assignmentId") UUID assignmentId);

    /**
     * 查询指定任务下已提交的评审报告（包括关联分配已提交的和独立确认/完成的报告）
     *
     * @param taskId 评审任务ID
     * @return 已提交的评审报告列表
     */
    @Select("""
            select r.*
            from public.review_report r
            left join public.review_assignment a on a.id = r.assignment_id
            where r.task_id = #{taskId}
              and (
                (r.assignment_id is not null and a.status = 'SUBMITTED')
                or (r.assignment_id is null and r.status in ('CONFIRMED', 'COMPLETED'))
              )
            order by r.updated_at desc
            """)
    @ResultMap("reviewReportResultMap")
    List<ReviewReportEntity> selectSubmittedByTaskId(@Param("taskId") UUID taskId);

    /**
     * 查询指定任务下所有有效分配的评审报告（排除已取消的分配）
     *
     * @param taskId 评审任务ID
     * @return 评审报告列表
     */
    @Select("""
            select r.*
            from public.review_report r
            join public.review_assignment a on a.id = r.assignment_id
            where r.task_id = #{taskId}
              and a.status <> 'CANCELLED'
            order by r.updated_at desc
            """)
    @ResultMap("reviewReportResultMap")
    List<ReviewReportEntity> selectActiveAssignmentReportsByTaskId(@Param("taskId") UUID taskId);

    /**
     * 查询指定任务下已提交的有效分配的评审报告
     *
     * @param taskId 评审任务ID
     * @return 已提交的评审报告列表
     */
    @Select("""
            select r.*
            from public.review_report r
            join public.review_assignment a on a.id = r.assignment_id
            where r.task_id = #{taskId}
              and a.status = 'SUBMITTED'
            order by r.updated_at desc
            """)
    @ResultMap("reviewReportResultMap")
    List<ReviewReportEntity> selectSubmittedActiveAssignmentReportsByTaskId(@Param("taskId") UUID taskId);
}
