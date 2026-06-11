package com.lqr.papermind.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysRoleMapper;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewBatchRequest;
import com.lqr.papermind.review.dto.ReviewBatchResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberUpdateRequest;
import com.lqr.papermind.review.dto.ReviewGroupRequest;
import com.lqr.papermind.review.dto.ReviewGroupResponse;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewBatchEntity;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import com.lqr.papermind.review.entity.ReviewGroupMemberEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewBatchMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMemberMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentRoles;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.service.ReviewGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewGroupServiceImpl implements ReviewGroupService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String MEMBER_ROLE_LEADER = "LEADER";
    private static final String MEMBER_ROLE_REVIEWER = "REVIEWER";

    private final ReviewBatchMapper batchMapper;
    private final ReviewGroupMapper groupMapper;
    private final ReviewGroupMemberMapper memberMapper;
    private final ReviewTaskMapper taskMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewConsensusMapper consensusMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    /**
     * 分页查询评审批次列表，按更新时间和创建时间降序排列。
     *
     * @param page 页码（从0开始）
     * @param size 每页大小，最大为100
     * @return 分页后的评审批次响应列表
     */
    @Override
    public PageResponse<ReviewBatchResponse> listBatches(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<ReviewBatchEntity> result = batchMapper.selectPage(
                new Page<>(safePage + 1L, safeSize),
                new LambdaQueryWrapper<ReviewBatchEntity>()
                        .orderByDesc(ReviewBatchEntity::getUpdatedAt)
                        .orderByDesc(ReviewBatchEntity::getCreatedAt)
        );
        return new PageResponse<>(
                result.getRecords().stream().map(ReviewBatchResponse::from).toList(),
                safePage,
                safeSize,
                result.getTotal()
        );
    }

    /**
     * 创建新的评审批次。
     *
     * @param operatorUserId 操作用户ID
     * @param request 批次创建请求，包含名称、描述、状态等信息
     * @return 创建后的评审批次响应
     */
    @Override
    @Transactional
    public ReviewBatchResponse createBatch(UUID operatorUserId, ReviewBatchRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ReviewBatchEntity batch = new ReviewBatchEntity();
        batch.setId(UUID.randomUUID());
        applyBatchRequest(batch, request);
        batch.setCreatedByUserId(operatorUserId);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchMapper.insert(batch);
        return ReviewBatchResponse.from(batch);
    }

    /**
     * 更新现有的评审批次信息。
     *
     * @param batchId 批次ID
     * @param request 批次更新请求，包含要更新的字段
     * @return 更新后的评审批次响应
     */
    @Override
    @Transactional
    public ReviewBatchResponse updateBatch(UUID batchId, ReviewBatchRequest request) {
        ReviewBatchEntity batch = requireBatch(batchId);
        applyBatchRequest(batch, request);
        batch.setUpdatedAt(OffsetDateTime.now());
        batchMapper.updateById(batch);
        return ReviewBatchResponse.from(batchMapper.selectById(batchId));
    }

    /**
     * 查询评审小组列表。如果batchId为null则查询所有小组，否则按批次ID筛选。
     *
     * @param batchId 批次ID，可为null
     * @return 评审小组响应列表
     */
    @Override
    public List<ReviewGroupResponse> listGroups(UUID batchId) {
        List<ReviewGroupEntity> groups = batchId == null
                ? groupMapper.selectList(new LambdaQueryWrapper<ReviewGroupEntity>()
                .orderByDesc(ReviewGroupEntity::getUpdatedAt)
                .orderByDesc(ReviewGroupEntity::getCreatedAt))
                : groupMapper.selectByBatchId(batchId);
        return groups.stream().map(this::toGroupResponse).toList();
    }

    /**
     * 创建新的评审小组，并自动将组长添加为小组成员。
     *
     * @param operatorUserId 操作用户ID
     * @param request 小组创建请求，包含批次ID、名称、组长用户ID、状态等信息
     * @return 创建后的评审小组响应
     */
    @Override
    @Transactional
    public ReviewGroupResponse createGroup(UUID operatorUserId, ReviewGroupRequest request) {
        requireBatch(request.batchId());
        SysUser leader = requireActiveReviewer(request.leaderUserId(), "组长用户不可用");
        OffsetDateTime now = OffsetDateTime.now();
        ReviewGroupEntity group = new ReviewGroupEntity();
        group.setId(UUID.randomUUID());
        group.setBatchId(request.batchId());
        group.setName(requireText(request.name(), "小组名称不能为空"));
        group.setLeaderUserId(leader.getId());
        group.setStatus(normalizeGroupStatus(request.status()));
        group.setCreatedByUserId(operatorUserId);
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        groupMapper.insert(group);
        insertMember(group.getId(), leader.getId(), MEMBER_ROLE_LEADER, now);
        return toGroupResponse(group);
    }

    /**
     * 更新现有的评审小组信息，确保组长为小组成员。
     *
     * @param groupId 小组ID
     * @param request 小组更新请求，包含要更新的字段
     * @return 更新后的评审小组响应
     */
    @Override
    @Transactional
    public ReviewGroupResponse updateGroup(UUID groupId, ReviewGroupRequest request) {
        ReviewGroupEntity group = requireGroup(groupId);
        requireBatch(request.batchId());
        SysUser leader = requireActiveReviewer(request.leaderUserId(), "组长用户不可用");
        group.setBatchId(request.batchId());
        group.setName(requireText(request.name(), "小组名称不能为空"));
        group.setLeaderUserId(leader.getId());
        group.setStatus(normalizeGroupStatus(request.status()));
        group.setUpdatedAt(OffsetDateTime.now());
        groupMapper.updateById(group);
        ensureLeaderMember(group.getId(), leader.getId());
        return toGroupResponse(groupMapper.selectById(groupId));
    }

    /**
     * 查询指定小组的活跃成员列表。
     *
     * @param groupId 小组ID
     * @return 小组成员响应列表
     */
    @Override
    public List<ReviewGroupMemberResponse> listGroupMembers(UUID groupId) {
        requireGroup(groupId);
        return memberMapper.selectActiveByGroupId(groupId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * 替换小组成员，先停用所有现有成员，然后重新添加指定成员列表。
     *
     * @param operatorUserId 操作用户ID
     * @param groupId 小组ID
     * @param request 成员更新请求，包含组长用户ID和成员用户ID列表
     * @return 替换后的小组成员响应列表
     */
    @Override
    @Transactional
    public List<ReviewGroupMemberResponse> replaceGroupMembers(UUID operatorUserId, UUID groupId, ReviewGroupMemberUpdateRequest request) {
        ReviewGroupEntity group = requireGroup(groupId);
        SysUser leader = requireActiveReviewer(request.leaderUserId(), "组长用户不可用");
        LinkedHashSet<UUID> memberIds = new LinkedHashSet<>();
        memberIds.add(leader.getId());
        if (request.memberUserIds() != null) {
            memberIds.addAll(request.memberUserIds());
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<ReviewGroupMemberEntity> insertedMembers = new ArrayList<>();
        memberMapper.deactivateByGroupId(groupId);
        for (UUID memberId : memberIds) {
            requireActiveReviewer(memberId, "小组成员用户不可用");
            ReviewGroupMemberEntity member = insertMember(groupId, memberId, memberId.equals(leader.getId()) ? MEMBER_ROLE_LEADER : MEMBER_ROLE_REVIEWER, now);
            insertedMembers.add(member);
        }
        group.setLeaderUserId(leader.getId());
        group.setUpdatedAt(now);
        groupMapper.updateById(group);
        return insertedMembers.stream().map(this::toMemberResponse).toList();
    }

    /**
     * 查询指定组长负责的所有活跃评审小组。
     *
     * @param leaderUserId 组长用户ID
     * @return 评审小组响应列表
     */
    @Override
    public List<ReviewGroupResponse> listLeaderGroups(UUID leaderUserId) {
        return groupMapper.selectActiveByLeader(leaderUserId).stream()
                .map(this::toGroupResponse)
                .toList();
    }

    /**
     * 组长查询指定小组的成员列表（需验证当前用户为该小组组长）。
     *
     * @param currentUserId 当前用户ID
     * @param groupId 小组ID
     * @return 小组成员响应列表
     */
    @Override
    public List<ReviewGroupMemberResponse> listGroupMembersForLeader(UUID currentUserId, UUID groupId) {
        requireManagedGroup(currentUserId, groupId);
        return memberMapper.selectActiveByGroupId(groupId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * 组长查询指定小组中未分配的评审任务列表。
     *
     * @param currentUserId 当前用户ID
     * @param groupId 小组ID
     * @return 未分配的评审任务摘要响应列表
     */
    @Override
    public List<AdminReviewTaskSummaryResponse> listUnassignedTasksForLeader(UUID currentUserId, UUID groupId) {
        requireManagedGroup(currentUserId, groupId);
        return taskMapper.selectUnassignedByGroupId(groupId).stream()
                .map(this::toTaskSummaryResponse)
                .toList();
    }

    /**
     * 组长查询指定小组的所有评审任务列表。
     *
     * @param currentUserId 当前用户ID
     * @param groupId 小组ID
     * @return 小组内所有评审任务摘要响应列表
     */
    @Override
    public List<AdminReviewTaskSummaryResponse> listGroupTasksForLeader(UUID currentUserId, UUID groupId) {
        requireManagedGroup(currentUserId, groupId);
        return taskMapper.selectByGroupId(groupId).stream()
                .map(this::toTaskSummaryResponse)
                .toList();
    }

    /**
     * 将批次请求应用到批次实体，设置名称、描述、状态、开始和结束时间。
     *
     * @param batch 批次实体
     * @param request 批次请求
     */
    private void applyBatchRequest(ReviewBatchEntity batch, ReviewBatchRequest request) {
        batch.setName(requireText(request.name(), "批次名称不能为空"));
        batch.setDescription(blankToNull(request.description()));
        batch.setStatus(normalizeBatchStatus(request.status()));
        batch.setStartsAt(request.startsAt());
        batch.setEndsAt(request.endsAt());
    }

    /**
     * 根据批次ID查找评审批次，不存在时抛出404异常。
     *
     * @param batchId 批次ID
     * @return 评审批次实体
     */
    private ReviewBatchEntity requireBatch(UUID batchId) {
        ReviewBatchEntity batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审批次不存在");
        }
        return batch;
    }

    /**
     * 根据小组ID查找评审小组，不存在时抛出404异常。
     *
     * @param groupId 小组ID
     * @return 评审小组实体
     */
    private ReviewGroupEntity requireGroup(UUID groupId) {
        ReviewGroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审小组不存在");
        }
        return group;
    }

    /**
     * 验证当前用户为指定小组的组长且小组状态为活跃，否则抛出异常。
     *
     * @param currentUserId 当前用户ID
     * @param groupId 小组ID
     * @return 评审小组实体
     */
    private ReviewGroupEntity requireManagedGroup(UUID currentUserId, UUID groupId) {
        ReviewGroupEntity group = requireGroup(groupId);
        if (currentUserId == null || !currentUserId.equals(group.getLeaderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能管理自己负责的小组");
        }
        if (!STATUS_ACTIVE.equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审小组不可用");
        }
        return group;
    }

    /**
     * 验证用户存在且状态为活跃，否则抛出异常。
     *
     * @param userId 用户ID
     * @param message 错误提示信息
     * @return 系统用户实体
     */
    private SysUser requireActiveUser(UUID userId, String message) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return user;
    }

    /**
     * 验证用户为活跃状态且具有评审员角色，否则抛出异常。
     *
     * @param userId 用户ID
     * @param message 错误提示信息
     * @return 系统用户实体
     */
    private SysUser requireActiveReviewer(UUID userId, String message) {
        SysUser user = requireActiveUser(userId, message);
        if (!roleMapper.selectRoleCodesByUserId(userId).contains(RoleCodes.REVIEWER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return user;
    }

    /**
     * 确保组长为小组成员，若不存在则自动添加。
     *
     * @param groupId 小组ID
     * @param leaderUserId 组长用户ID
     */
    private void ensureLeaderMember(UUID groupId, UUID leaderUserId) {
        if (memberMapper.selectActiveByGroupAndUser(groupId, leaderUserId) == null) {
            insertMember(groupId, leaderUserId, MEMBER_ROLE_LEADER, OffsetDateTime.now());
        }
    }

    /**
     * 向小组中插入新成员。
     *
     * @param groupId 小组ID
     * @param userId 用户ID
     * @param memberRole 成员角色（LEADER或REVIEWER）
     * @param now 当前时间
     * @return 插入的小组成员实体
     */
    private ReviewGroupMemberEntity insertMember(UUID groupId, UUID userId, String memberRole, OffsetDateTime now) {
        ReviewGroupMemberEntity member = new ReviewGroupMemberEntity();
        member.setId(UUID.randomUUID());
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setMemberRole(memberRole);
        member.setStatus(STATUS_ACTIVE);
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
        return member;
    }

    /**
     * 将评审小组实体转换为响应对象，包含组长信息和统计信息。
     *
     * @param group 评审小组实体
     * @return 评审小组响应对象
     */
    private ReviewGroupResponse toGroupResponse(ReviewGroupEntity group) {
        SysUser leader = group.getLeaderUserId() == null ? null : userMapper.selectById(group.getLeaderUserId());
        return new ReviewGroupResponse(
                group.getId(),
                group.getBatchId(),
                group.getName(),
                group.getLeaderUserId(),
                leader == null ? null : leader.getUsername(),
                leader == null ? null : leader.getDisplayName(),
                group.getStatus(),
                memberMapper.countActiveByGroupId(group.getId()),
                groupMapper.countTasksByGroupId(group.getId()),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    /**
     * 将小组成员实体转换为响应对象，包含用户信息。
     *
     * @param member 小组成员实体
     * @return 小组成员响应对象
     */
    private ReviewGroupMemberResponse toMemberResponse(ReviewGroupMemberEntity member) {
        SysUser user = userMapper.selectById(member.getUserId());
        return new ReviewGroupMemberResponse(
                member.getId(),
                member.getGroupId(),
                member.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                member.getMemberRole(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getRemovedAt(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    /**
     * 将评审任务实体转换为管理端摘要响应对象，包含分配信息、负责人和共识状态。
     *
     * @param task 评审任务实体
     * @return 管理端评审任务摘要响应对象
     */
    private AdminReviewTaskSummaryResponse toTaskSummaryResponse(ReviewTaskEntity task) {
        List<ReviewAssignmentEntity> assignments = assignmentMapper.selectByTaskId(task.getId());
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(task.getId());
        UUID leadReviewerUserId = assignments.stream()
                .filter(assignment -> ReviewAssignmentRoles.LEAD.equals(assignment.getRole()))
                .map(ReviewAssignmentEntity::getReviewerUserId)
                .findFirst()
                .orElse(task.getLeaderUserId());
        SysUser leadReviewer = leadReviewerUserId == null ? null : userMapper.selectById(leadReviewerUserId);
        return new AdminReviewTaskSummaryResponse(
                task.getId(),
                task.getDocumentId(),
                task.getSubmitterUserId(),
                task.getSourceId(),
                task.getTitle(),
                task.getStatus(),
                assignments.stream()
                        .filter(assignment -> !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus()))
                        .count(),
                assignments.stream()
                        .filter(assignment -> ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus()))
                        .count(),
                leadReviewerUserId,
                leadReviewer == null ? null : leadReviewer.getUsername(),
                leadReviewer == null ? null : leadReviewer.getDisplayName(),
                assignmentMapper.maxDueAtByTaskId(task.getId()),
                consensus == null ? null : consensus.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    /**
     * 标准化批次状态值，将空值默认为DRAFT，不合法状态抛出异常。
     *
     * @param status 原始状态值
     * @return 标准化后的状态值（大写）
     */
    private String normalizeBatchStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_DRAFT;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("DRAFT", "ACTIVE", "CLOSED", "ARCHIVED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批次状态不合法");
        }
        return normalized;
    }

    /**
     * 标准化小组状态值，将空值默认为ACTIVE，不合法状态抛出异常。
     *
     * @param status 原始状态值
     * @return 标准化后的状态值（大写）
     */
    private String normalizeGroupStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小组状态不合法");
        }
        return normalized;
    }

    /**
     * 验证文本值不为空白，为空白时抛出异常。
     *
     * @param value 待验证的文本值
     * @param message 错误提示信息
     * @return 去除首尾空格后的文本值
     */
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /**
     * 将空白字符串转换为null，非空白字符串去除首尾空格后返回。
     *
     * @param value 原始字符串
     * @return 处理后的字符串，空白时返回null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
