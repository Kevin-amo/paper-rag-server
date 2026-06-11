package com.lqr.papermind.review.model;

/**
 * 评审任务状态枚举类
 * 定义评审任务生命周期中的所有可能状态
 */
public final class ReviewTaskStatuses {
    /**
     * 待处理状态：任务已创建但未开始处理
     */
    public static final String PENDING = "PENDING";

    /**
     * 待分配状态：任务已准备就绪，等待分配给评审人员
     */
    public static final String PENDING_ASSIGNMENT = "PENDING_ASSIGNMENT";

    /**
     * 已分配状态：任务已分配给评审人员，等待开始评审
     */
    public static final String ASSIGNED = "ASSIGNED";

    /**
     * 评审中状态：评审人员正在对论文进行评审
     */
    public static final String REVIEWING = "REVIEWING";

    /**
     * 评审进行中状态：评审工作已开始但尚未完成
     */
    public static final String IN_REVIEW = "IN_REVIEW";

    /**
     * 已提交状态：评审人员已完成评审并提交评审意见
     */
    public static final String SUBMITTED = "SUBMITTED";

    /**
     * 已完成状态：所有评审工作已完成
     */
    public static final String COMPLETED = "COMPLETED";

    /**
     * 共识已确认状态：评审意见已达成共识并确认
     */
    public static final String CONSENSUS_CONFIRMED = "CONSENSUS_CONFIRMED";

    /**
     * 需要重新评审状态：需要对论文进行重新评审
     */
    public static final String NEEDS_REVIEW = "NEEDS_REVIEW";

    /**
     * 私有构造函数，防止实例化
     */
    private ReviewTaskStatuses() {
    }
}
