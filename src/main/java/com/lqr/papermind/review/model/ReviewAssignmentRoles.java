package com.lqr.papermind.review.model;

/**
 * 评审分配角色枚举类
 * 定义评审分配过程中的所有可能角色
 */
public final class ReviewAssignmentRoles {
    /**
     * 主审角色：负责评审任务的整体管理和协调
     */
    public static final String LEAD = "LEAD";

    /**
     * 评审员角色：负责具体论文的评审工作
     */
    public static final String REVIEWER = "REVIEWER";

    /**
     * 仲裁者角色：负责评审意见冲突时的仲裁决策
     */
    public static final String ARBITER = "ARBITER";

    /**
     * 私有构造函数，防止实例化
     */
    private ReviewAssignmentRoles() {
    }
}
