package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysRoleMapper;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.review.dto.ReviewGroupMemberUpdateRequest;
import com.lqr.papermind.review.dto.ReviewGroupRequest;
import com.lqr.papermind.review.entity.ReviewBatchEntity;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import com.lqr.papermind.review.entity.ReviewGroupMemberEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewBatchMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMemberMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewGroupServiceImplTest {

    private ReviewBatchMapper batchMapper;
    private ReviewGroupMapper groupMapper;
    private ReviewGroupMemberMapper memberMapper;
    private ReviewTaskMapper taskMapper;
    private ReviewAssignmentMapper assignmentMapper;
    private ReviewConsensusMapper consensusMapper;
    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private ReviewGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(ReviewBatchMapper.class);
        groupMapper = mock(ReviewGroupMapper.class);
        memberMapper = mock(ReviewGroupMemberMapper.class);
        taskMapper = mock(ReviewTaskMapper.class);
        assignmentMapper = mock(ReviewAssignmentMapper.class);
        consensusMapper = mock(ReviewConsensusMapper.class);
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        service = new ReviewGroupServiceImpl(batchMapper, groupMapper, memberMapper, taskMapper, assignmentMapper, consensusMapper, userMapper, roleMapper);
    }

    @Test
    void createGroupCreatesLeaderMembershipAndReturnsLeaderProfile() {
        UUID operatorId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        when(batchMapper.selectById(batchId)).thenReturn(batch(batchId));
        when(userMapper.selectById(leaderId)).thenReturn(user(leaderId, "leader-a", "组长A", "ACTIVE"));
        when(roleMapper.selectRoleCodesByUserId(leaderId)).thenReturn(List.of(RoleCodes.REVIEWER));

        var response = service.createGroup(operatorId, new ReviewGroupRequest(batchId, "第一评审组", leaderId, "ACTIVE"));

        ArgumentCaptor<ReviewGroupEntity> groupCaptor = ArgumentCaptor.forClass(ReviewGroupEntity.class);
        ArgumentCaptor<ReviewGroupMemberEntity> memberCaptor = ArgumentCaptor.forClass(ReviewGroupMemberEntity.class);
        verify(groupMapper).insert(groupCaptor.capture());
        verify(memberMapper).insert(memberCaptor.capture());
        ReviewGroupEntity savedGroup = groupCaptor.getValue();
        ReviewGroupMemberEntity savedMember = memberCaptor.getValue();
        assertThat(savedGroup.getId()).isNotNull();
        assertThat(savedGroup.getBatchId()).isEqualTo(batchId);
        assertThat(savedGroup.getName()).isEqualTo("第一评审组");
        assertThat(savedGroup.getLeaderUserId()).isEqualTo(leaderId);
        assertThat(savedGroup.getCreatedByUserId()).isEqualTo(operatorId);
        assertThat(savedGroup.getStatus()).isEqualTo("ACTIVE");
        assertThat(savedMember.getGroupId()).isEqualTo(savedGroup.getId());
        assertThat(savedMember.getUserId()).isEqualTo(leaderId);
        assertThat(savedMember.getMemberRole()).isEqualTo("LEADER");
        assertThat(savedMember.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.id()).isEqualTo(savedGroup.getId());
        assertThat(response.leaderUsername()).isEqualTo("leader-a");
        assertThat(response.leaderDisplayName()).isEqualTo("组长A");
    }

    @Test
    void createGroupRejectsDisabledLeader() {
        UUID batchId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        when(batchMapper.selectById(batchId)).thenReturn(batch(batchId));
        when(userMapper.selectById(leaderId)).thenReturn(user(leaderId, "leader-a", "组长A", "DISABLED"));

        assertThatThrownBy(() -> service.createGroup(UUID.randomUUID(), new ReviewGroupRequest(batchId, "第一评审组", leaderId, "ACTIVE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("组长用户不可用");
    }

    @Test
    void createGroupRejectsLeaderWithoutReviewerRole() {
        UUID batchId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        when(batchMapper.selectById(batchId)).thenReturn(batch(batchId));
        when(userMapper.selectById(leaderId)).thenReturn(user(leaderId, "leader-a", "组长A", "ACTIVE"));
        when(roleMapper.selectRoleCodesByUserId(leaderId)).thenReturn(List.of(RoleCodes.USER));

        assertThatThrownBy(() -> service.createGroup(UUID.randomUUID(), new ReviewGroupRequest(batchId, "第一评审组", leaderId, "ACTIVE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("组长用户不可用");
    }

    @Test
    void replaceGroupMembersAlwaysKeepsLeaderAsActiveMember() {
        UUID groupId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ReviewGroupEntity group = group(groupId, UUID.randomUUID(), leaderId, "ACTIVE");
        when(groupMapper.selectById(groupId)).thenReturn(group);
        when(userMapper.selectById(leaderId)).thenReturn(user(leaderId, "leader-a", "组长A", "ACTIVE"));
        when(userMapper.selectById(memberId)).thenReturn(user(memberId, "reviewer-a", "评审员A", "ACTIVE"));
        when(roleMapper.selectRoleCodesByUserId(leaderId)).thenReturn(List.of(RoleCodes.REVIEWER));
        when(roleMapper.selectRoleCodesByUserId(memberId)).thenReturn(List.of(RoleCodes.REVIEWER));

        var members = service.replaceGroupMembers(UUID.randomUUID(), groupId, new ReviewGroupMemberUpdateRequest(leaderId, List.of(memberId)));

        ArgumentCaptor<ReviewGroupMemberEntity> memberCaptor = ArgumentCaptor.forClass(ReviewGroupMemberEntity.class);
        verify(memberMapper).deactivateByGroupId(groupId);
        verify(memberMapper, org.mockito.Mockito.times(2)).insert(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues())
                .extracting(ReviewGroupMemberEntity::getUserId)
                .containsExactly(leaderId, memberId);
        assertThat(memberCaptor.getAllValues())
                .extracting(ReviewGroupMemberEntity::getMemberRole)
                .containsExactly("LEADER", "REVIEWER");
        assertThat(members).hasSize(2);
        assertThat(members.getFirst().memberRole()).isEqualTo("LEADER");
        assertThat(members.get(1).memberRole()).isEqualTo("REVIEWER");
    }

    @Test
    void listLeaderGroupsUsesLeaderUserScope() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ReviewGroupEntity group = group(groupId, UUID.randomUUID(), leaderId, "ACTIVE");
        when(groupMapper.selectActiveByLeader(leaderId)).thenReturn(List.of(group));
        when(userMapper.selectById(leaderId)).thenReturn(user(leaderId, "leader-a", "组长A", "ACTIVE"));

        var groups = service.listLeaderGroups(leaderId);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().id()).isEqualTo(groupId);
        assertThat(groups.getFirst().leaderUserId()).isEqualTo(leaderId);
        verify(groupMapper).selectActiveByLeader(leaderId);
    }

    @Test
    void listUnassignedTasksForLeaderUsesManagedGroupScope() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskEntity task = task(taskId, groupId, leaderId);
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, UUID.randomUUID(), leaderId, "ACTIVE"));
        when(taskMapper.selectUnassignedByGroupId(groupId)).thenReturn(List.of(task));
        when(assignmentMapper.selectByTaskId(taskId)).thenReturn(List.of());

        var tasks = service.listUnassignedTasksForLeader(leaderId, groupId);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().id()).isEqualTo(taskId);
        assertThat(tasks.getFirst().leadReviewerUserId()).isEqualTo(leaderId);
        verify(taskMapper).selectUnassignedByGroupId(groupId);
    }

    @Test
    void listGroupTasksForLeaderUsesManagedGroupScope() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskEntity task = task(taskId, groupId, leaderId);
        task.setStatus("SUBMITTED");
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, UUID.randomUUID(), leaderId, "ACTIVE"));
        when(taskMapper.selectByGroupId(groupId)).thenReturn(List.of(task));
        when(assignmentMapper.selectByTaskId(taskId)).thenReturn(List.of());

        var tasks = service.listGroupTasksForLeader(leaderId, groupId);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().id()).isEqualTo(taskId);
        assertThat(tasks.getFirst().status()).isEqualTo("SUBMITTED");
        verify(taskMapper).selectByGroupId(groupId);
    }

    @Test
    void listGroupMembersForLeaderRejectsOtherGroupLeader() {
        UUID groupId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID otherLeaderId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, UUID.randomUUID(), otherLeaderId, "ACTIVE"));

        assertThatThrownBy(() -> service.listGroupMembersForLeader(currentUserId, groupId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("只能管理自己负责的小组");
    }

    private ReviewBatchEntity batch(UUID batchId) {
        ReviewBatchEntity batch = new ReviewBatchEntity();
        batch.setId(batchId);
        batch.setName("批次");
        batch.setStatus("ACTIVE");
        return batch;
    }

    private ReviewGroupEntity group(UUID groupId, UUID batchId, UUID leaderId, String status) {
        ReviewGroupEntity group = new ReviewGroupEntity();
        group.setId(groupId);
        group.setBatchId(batchId);
        group.setName("第一评审组");
        group.setLeaderUserId(leaderId);
        group.setStatus(status);
        return group;
    }

    private ReviewTaskEntity task(UUID taskId, UUID groupId, UUID leaderId) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setDocumentId(UUID.randomUUID());
        task.setSubmitterUserId(UUID.randomUUID());
        task.setSourceId("source-1");
        task.setTitle("待分配论文");
        task.setStatus("PENDING_ASSIGNMENT");
        task.setGroupId(groupId);
        task.setLeaderUserId(leaderId);
        return task;
    }

    private SysUser user(UUID userId, String username, String displayName, String status) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus(status);
        return user;
    }
}