package com.lqr.papermind.review.model;

/**
 * 评审分配状态枚举类
 * 定义评审分配过程中的所有可能状态
 */
public final class ReviewAssignmentStatuses {
    /**
     * 已分配状态：评审任务已分配给评审人员
     */
    public static final String ASSIGNED = "ASSIGNED";

    /**
     * 评审中状态：评审人员正在执行评审工作
     */
    public static final String REVIEWING = "REVIEWING";

    /**
     * 已提交状态：评审人员已完成评审并提交结果
     */
    public static final String SUBMITTED = "SUBMITTED";

    /**
     * 已退回状态：评审任务被退回给分配者
     */
    public static final String RETURNED = "RETURNED";

    /**
     * 已取消状态：评审分配任务被取消
     */
    public static final String CANCELLED = "CANCELLED";

    /**
     * 私有构造函数，防止实例化
     */
    private ReviewAssignmentStatuses() {
    }
}
