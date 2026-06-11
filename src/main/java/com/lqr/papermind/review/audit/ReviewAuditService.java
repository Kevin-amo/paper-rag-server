package com.lqr.papermind.review.audit;

import com.lqr.papermind.review.entity.ReviewAuditLogEntity;
import com.lqr.papermind.review.mapper.ReviewAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewAuditService {

    private final ReviewAuditLogMapper mapper;

    /**
     * 追加一条评审审计日志。
     * 记录操作人、操作类型、变更前后快照及差异信息。
     *
     * @param taskId          关联任务唯一标识
     * @param actorUserId     操作人用户唯一标识
     * @param action          操作类型（如 CREATE、UPDATE、STATUS_CHANGE 等）
     * @param message         操作备注信息
     * @param beforeSnapshot  变更前状态快照，可为 null
     * @param afterSnapshot   变更后状态快照，可为 null
     * @param clientInfo      客户端信息（如 IP、User-Agent 等），可为 null
     */
    public void append(UUID taskId,
                       UUID actorUserId,
                       String action,
                       String message,
                       Map<String, Object> beforeSnapshot,
                       Map<String, Object> afterSnapshot,
                       Map<String, Object> clientInfo) {
        ReviewAuditLogEntity log = new ReviewAuditLogEntity();
        log.setId(UUID.randomUUID());
        log.setTaskId(taskId);
        log.setOperatorUserId(actorUserId);
        log.setAction(action);
        log.setNote(message);
        log.setSnapshot(afterSnapshot);
        log.setBeforeSnapshot(beforeSnapshot);
        log.setAfterSnapshot(afterSnapshot);
        log.setDiff(diff(beforeSnapshot, afterSnapshot));
        log.setClientInfo(clientInfo);
        log.setCreatedAt(OffsetDateTime.now());
        mapper.insert(log);
    }

    /**
     * 比较两个快照，生成差异映射。
     * 仅包含值发生变化的键，每个键对应一个包含 before 和 after 的变更描述。
     *
     * @param before 变更前快照，可为 null
     * @param after  变更后快照，可为 null
     * @return 差异映射（键 → {before, after}），无差异时返回空 Map
     */
    private Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> result = new LinkedHashMap<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (before != null) {
            keys.addAll(before.keySet());
        }
        if (after != null) {
            keys.addAll(after.keySet());
        }
        for (String key : keys) {
            Object beforeValue = before == null ? null : before.get(key);
            Object afterValue = after == null ? null : after.get(key);
            if (!Objects.equals(beforeValue, afterValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("before", beforeValue);
                change.put("after", afterValue);
                result.put(key, change);
            }
        }
        return result;
    }
}
