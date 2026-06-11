package com.lqr.papermind.review.model;

/**
 * 评审共识状态枚举类
 * 定义评审共识过程中的所有可能状态
 */
public final class ReviewConsensusStatuses {
    /**
     * 草稿状态：共识内容正在起草中
     */
    public static final String DRAFT = "DRAFT";

    /**
     * 讨论中状态：评审人员正在讨论共识内容
     */
    public static final String IN_DISCUSSION = "IN_DISCUSSION";

    /**
     * 已确认状态：共识已达成并正式确认
     */
    public static final String CONFIRMED = "CONFIRMED";

    /**
     * 已归档状态：共识已存档，不再活跃
     */
    public static final String ARCHIVED = "ARCHIVED";

    /**
     * 私有构造函数，防止实例化
     */
    private ReviewConsensusStatuses() {
    }
}
