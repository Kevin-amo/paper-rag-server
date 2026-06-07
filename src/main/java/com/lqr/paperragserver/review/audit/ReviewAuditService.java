package com.lqr.paperragserver.review.audit;

import com.lqr.paperragserver.review.entity.ReviewAuditLogEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
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