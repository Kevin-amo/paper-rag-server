# Paper Review System Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 single-reviewer intelligent paper review loop: configurable scoring rules, stable AI output parsing, normalized risk persistence, expert adjustment traceability, and a tabbed reviewer UI.

**Architecture:** Extend the existing Spring Boot + Vue application without replacing current upload, structured parsing, review task, and report flows. Keep existing `/reviews` endpoints compatible, add focused parser/risk/audit units, and persist normalized risk items while retaining existing JSON report fields.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis-Plus, PostgreSQL JSONB, JUnit 5, AssertJ, Mockito, Vue 3, TypeScript, Element Plus, Vite.

---

## Scope

Phase 1 implements a single-reviewer loop:

- Schema extensions for parser, rubric, report, audit metadata.
- Configurable scoring rules in existing review criteria.
- AI output parser extraction and normalization.
- Deterministic reference-format risk checking.
- Normalized risk item table and risk state API.
- Manual-delta and audit diff capture.
- Reviewer UI tabs for parse, scores, risks, comments, and audit metadata.

Separate future plans should cover multi-expert assignment/consensus and evaluation dashboards.

## File Structure

### Backend schema/entities/DTOs

- Modify: `E:/Java_code/paper-rag-server/src/main/resources/sql/paper-rag.sql` — phase-one schema additions.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/document/structured/entity/PaperStructuredParseEntity.java` — parser/model/prompt versions and quality metrics.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/document/structured/dto/PaperStructuredParseResponse.java` — expose parse metadata.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewCriterionEntity.java` — scoring rules and rubric metadata.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewReportEntity.java` — model/prompt/rubric metadata and manual delta.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewAuditLogEntity.java` — before/after/diff/client info.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewRiskItemEntity.java` — normalized risk entity.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/mapper/ReviewRiskItemMapper.java` — risk mapper.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewCriterionRequest.java` — accept rubric metadata.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewCriterionResponse.java` — return rubric metadata.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewReportResponse.java` — return report metadata.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewRiskItemResponse.java` — risk response.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewRiskUpdateRequest.java` — risk state update request.

### Backend services/API

- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/assessment/ReviewOutputParser.java` — extract, repair, validate, normalize model JSON.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/risk/ReferenceFormatChecker.java` — deterministic reference checker.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/risk/ReviewRiskService.java` — risk persistence and state transitions.
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/audit/ReviewAuditService.java` — audit entry creation with shallow diffs.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/ReviewService.java` — risk-facing methods.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java` — wire parser/risk/audit/checker.
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/web/ReviewController.java` — risk endpoints.

### Frontend

- Modify: `E:/Java_code/paper-rag-server/frontend/src/types/index.ts` — review risk, scoring rule, report metadata types.
- Modify: `E:/Java_code/paper-rag-server/frontend/src/api/reviews.ts` — risk endpoints.
- Modify: `E:/Java_code/paper-rag-server/frontend/src/composables/useReviews.ts` — risk state loading/updating.
- Modify: `E:/Java_code/paper-rag-server/frontend/src/views/review/ReviewWorkspaceView.vue` — tabbed detail UI.

### Tests

- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/assessment/ReviewOutputParserTest.java`.
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/risk/ReferenceFormatCheckerTest.java`.
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/risk/ReviewRiskServiceTest.java`.
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/audit/ReviewAuditServiceTest.java`.
- Modify: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImplTest.java`.

---

## Task 1: Database Schema Extensions

**Files:**
- Modify: `E:/Java_code/paper-rag-server/src/main/resources/sql/paper-rag.sql`

- [ ] **Step 1: Locate current schema blocks**

Run:

```powershell
Select-String -Path 'src/main/resources/sql/paper-rag.sql' -Pattern 'create table if not exists public.paper_structured_parse','create table if not exists public.review_criterion','create table if not exists public.review_report','create table if not exists public.review_audit_log'
```

Expected: four matches.

- [ ] **Step 2: Extend `paper_structured_parse`**

Add after `raw_model_output text,`:

```sql
    parser_version varchar(64),
    model_version varchar(128),
    prompt_version varchar(64),
    quality_metrics jsonb not null default '{}'::jsonb,
```

Add comment:

```sql
comment on column public.paper_structured_parse.quality_metrics is '结构化解析质量指标 JSON，例如章节覆盖率、字段覆盖率和整体置信度';
```

- [ ] **Step 3: Extend `review_criterion`**

Add after `weight int not null default 20,`:

```sql
    version int not null default 1,
    category varchar(64),
    evidence_required boolean not null default true,
    scoring_rules jsonb not null default '[]'::jsonb,
```

Change constraints to include:

```sql
    constraint chk_review_criterion_version check (version >= 1)
```

Update seed insert to include `version, category, evidence_required, scoring_rules`. Use this exact scoring rules JSON for `POLICY` and equivalent three-level JSON for the other seeded dimensions:

```sql
'[{"level":"excellent","range":[90,100],"description":"无政治不当表述，价值导向明确，相关论述客观规范"},{"level":"good","range":[75,89],"description":"整体符合政策导向要求，个别表达建议进一步规范"},{"level":"review","range":[0,74],"description":"存在需要专家重点复核的政策导向或敏感表达风险"}]'::jsonb
```

- [ ] **Step 4: Extend `review_report`**

Add after `raw_model_output jsonb not null default '{}'::jsonb,`:

```sql
    criterion_version int,
    model_version varchar(128),
    prompt_version varchar(64),
    confidence numeric(5,4),
    manual_delta jsonb not null default '{}'::jsonb,
```

Add constraint:

```sql
    constraint chk_review_report_confidence check (confidence is null or (confidence >= 0 and confidence <= 1)),
```

- [ ] **Step 5: Add `review_risk_item`**

Add before `create table if not exists public.review_audit_log`:

```sql
create table if not exists public.review_risk_item (
    id uuid primary key default uuid_generate_v4(),
    report_id uuid not null references public.review_report(id) on delete cascade,
    task_id uuid not null references public.review_task(id) on delete cascade,
    risk_type varchar(64) not null,
    risk_level varchar(32) not null,
    evidence text,
    evidence_location jsonb not null default '{}'::jsonb,
    suggestion text,
    detector varchar(64),
    confidence numeric(5,4),
    status varchar(32) not null default 'OPEN',
    reviewer_note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_review_risk_level check (risk_level in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint chk_review_risk_confidence check (confidence is null or (confidence >= 0 and confidence <= 1)),
    constraint chk_review_risk_status check (status in ('OPEN', 'CONFIRMED', 'IGNORED', 'RESOLVED'))
);

create index if not exists idx_review_risk_item_report_status on public.review_risk_item using btree (report_id, status);
create index if not exists idx_review_risk_item_task_level on public.review_risk_item using btree (task_id, risk_level);
```

- [ ] **Step 6: Extend `review_audit_log`**

Add after `snapshot jsonb not null default '{}'::jsonb,`:

```sql
    before_snapshot jsonb not null default '{}'::jsonb,
    after_snapshot jsonb not null default '{}'::jsonb,
    diff jsonb not null default '{}'::jsonb,
    client_info jsonb not null default '{}'::jsonb,
```

- [ ] **Step 7: Verify SQL additions**

Run:

```powershell
Select-String -Path 'src/main/resources/sql/paper-rag.sql' -Pattern 'review_risk_item','scoring_rules','manual_delta','quality_metrics','before_snapshot'
```

Expected: at least one match for each pattern.

- [ ] **Step 8: Commit schema changes**

```powershell
git add src/main/resources/sql/paper-rag.sql
git commit -m "feat: extend review phase one schema"
```

---

## Task 2: Entity and DTO Metadata Extensions

**Files:**
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/document/structured/entity/PaperStructuredParseEntity.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/document/structured/dto/PaperStructuredParseResponse.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewCriterionEntity.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewReportEntity.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewAuditLogEntity.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewCriterionRequest.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewCriterionResponse.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewReportResponse.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java`

- [ ] **Step 1: Run baseline compile**

```powershell
mvn -q -DskipTests compile
```

Expected: exit code `0`. If it fails before edits, record the exact failure before continuing.

- [ ] **Step 2: Extend structured parse Java model**

In `PaperStructuredParseEntity.java`, add after `rawModelOutput`:

```java
    private String parserVersion;
    private String modelVersion;
    private String promptVersion;

    @TableField(value = "quality_metrics", typeHandler = JsonbTypeHandler.class)
    private Object qualityMetrics;
```

In `PaperStructuredParseResponse.java`, add record fields after `rawModelOutput`:

```java
        String parserVersion,
        String modelVersion,
        String promptVersion,
        Object qualityMetrics,
```

Pass these values in `from(...)` after `entity.getRawModelOutput()`:

```java
                entity.getParserVersion(),
                entity.getModelVersion(),
                entity.getPromptVersion(),
                entity.getQualityMetrics(),
```

- [ ] **Step 3: Extend criterion entity and DTOs**

In `ReviewCriterionEntity.java`, change the table annotation to:

```java
@TableName(value = "public.review_criterion", autoResultMap = true)
```

Add imports:

```java
import com.baomidou.mybatisplus.annotation.TableField;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
```

Add after `weight`:

```java
    private Integer version;
    private String category;
    private Boolean evidenceRequired;

    @TableField(value = "scoring_rules", typeHandler = JsonbTypeHandler.class)
    private Object scoringRules;
```

Change `ReviewCriterionRequest` to include:

```java
@Min(1) Integer version,
String category,
Boolean evidenceRequired,
List<Map<String, Object>> scoringRules,
```

Add imports to `ReviewCriterionRequest.java`:

```java
import java.util.List;
import java.util.Map;
```

Change `ReviewCriterionResponse` to include after `weight`:

```java
int version,
String category,
boolean evidenceRequired,
Object scoringRules,
```

Pass values in `from(...)`:

```java
entity.getVersion() == null ? 1 : entity.getVersion(),
entity.getCategory(),
entity.getEvidenceRequired() == null || entity.getEvidenceRequired(),
entity.getScoringRules(),
```

- [ ] **Step 4: Extend report and audit entities/DTOs**

In `ReviewReportEntity.java`, add import:

```java
import java.math.BigDecimal;
```

Add after `rawModelOutput`:

```java
    private Integer criterionVersion;
    private String modelVersion;
    private String promptVersion;
    private BigDecimal confidence;

    @TableField(value = "manual_delta", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> manualDelta;
```

In `ReviewReportResponse.java`, add import:

```java
import java.math.BigDecimal;
```

Add fields after `risks`:

```java
Integer criterionVersion,
String modelVersion,
String promptVersion,
BigDecimal confidence,
Map<String, Object> manualDelta,
```

Pass values in `from(...)`:

```java
entity.getCriterionVersion(),
entity.getModelVersion(),
entity.getPromptVersion(),
entity.getConfidence(),
entity.getManualDelta(),
```

In `ReviewAuditLogEntity.java`, add after `snapshot`:

```java
    @TableField(value = "before_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> beforeSnapshot;

    @TableField(value = "after_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> afterSnapshot;

    @TableField(value = "diff", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> diff;

    @TableField(value = "client_info", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> clientInfo;
```

- [ ] **Step 5: Apply criterion request fields**

In `ReviewServiceImpl.applyCriterionRequest`, after `entity.setWeight(...)`, add:

```java
        entity.setVersion(request.version() == null ? 1 : request.version());
        entity.setCategory(blankToNull(request.category()));
        entity.setEvidenceRequired(request.evidenceRequired() == null || request.evidenceRequired());
        entity.setScoringRules(request.scoringRules() == null ? List.of() : request.scoringRules());
```

`ReviewServiceImpl` already imports `java.util.List`; keep that import.

- [ ] **Step 6: Verify compile**

```powershell
mvn -q -DskipTests compile
```

Expected: exit code `0`.

- [ ] **Step 7: Commit metadata extensions**

```powershell
git add src/main/java/com/lqr/paperragserver/document/structured/entity/PaperStructuredParseEntity.java src/main/java/com/lqr/paperragserver/document/structured/dto/PaperStructuredParseResponse.java src/main/java/com/lqr/paperragserver/review/entity/ReviewCriterionEntity.java src/main/java/com/lqr/paperragserver/review/entity/ReviewReportEntity.java src/main/java/com/lqr/paperragserver/review/entity/ReviewAuditLogEntity.java src/main/java/com/lqr/paperragserver/review/dto/ReviewCriterionRequest.java src/main/java/com/lqr/paperragserver/review/dto/ReviewCriterionResponse.java src/main/java/com/lqr/paperragserver/review/dto/ReviewReportResponse.java src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java
git commit -m "feat: expose review phase one metadata"
```

---

## Task 3: Review Output Parser

**Files:**
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/assessment/ReviewOutputParser.java`
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/assessment/ReviewOutputParserTest.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java`
- Modify: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImplTest.java`

- [ ] **Step 1: Write failing parser tests**

Create `ReviewOutputParserTest.java`:

```java
package com.lqr.paperragserver.review.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewOutputParserTest {
    private final ReviewOutputParser parser = new ReviewOutputParser(new ObjectMapper());

    @Test
    void parseShouldExtractMarkdownJsonRepairTrailingCommasAndClampScores() {
        Map<String, Object> parsed = parser.parse("""
                ```json
                {
                  "paperSections": {"title": "论文A",},
                  "scores": [{"code": "LOGIC", "score": 130, "maxScore": 100, "confidence": 1.4,}],
                  "comments": {"summary": "可读",},
                  "risks": [],
                  "totalScore": 120,
                  "finalRecommendation": "建议修改后通过",
                }
                ```
                """);
        assertThat(parsed.get("totalScore")).isEqualTo(100);
        List<?> scores = (List<?>) parsed.get("scores");
        Map<?, ?> first = (Map<?, ?>) scores.get(0);
        assertThat(first.get("score")).isEqualTo(100);
        assertThat(first.get("confidence")).isEqualTo(1.0);
    }

    @Test
    void parseShouldRejectTextWithoutJson() {
        assertThatThrownBy(() -> parser.parse("没有 JSON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 JSON 对象");
    }
}
```

- [ ] **Step 2: Run failing test**

```powershell
mvn -q -Dtest=ReviewOutputParserTest test
```

Expected: fails because `ReviewOutputParser` is not implemented.

- [ ] **Step 3: Implement parser**

Create `ReviewOutputParser.java`:

```java
package com.lqr.paperragserver.review.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReviewOutputParser {
    private final ObjectMapper objectMapper;

    public Map<String, Object> parse(String modelText) {
        String json = extractJson(modelText);
        Map<String, Object> parsed = read(json);
        normalize(parsed);
        return parsed;
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException first) {
            try {
                return objectMapper.readValue(json.replaceAll(",\\s*([}\\]])", "$1"), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException second) {
                throw new IllegalArgumentException("模型评审结果不是有效 JSON", second);
            }
        }
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型评审结果为空");
        }
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("模型评审结果缺少 JSON 对象");
        }
        int end = text.lastIndexOf('}');
        if (end <= start) {
            throw new IllegalArgumentException("模型评审结果 JSON 对象不完整");
        }
        return text.substring(start, end + 1);
    }

    private void normalize(Map<String, Object> parsed) {
        parsed.putIfAbsent("paperSections", Map.of());
        parsed.putIfAbsent("scores", List.of());
        parsed.putIfAbsent("comments", Map.of());
        parsed.putIfAbsent("risks", List.of());
        parsed.put("totalScore", clampInt(parsed.get("totalScore"), 0, 100));
        if (parsed.get("scores") instanceof List<?> list) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> next = new LinkedHashMap<>();
                    map.forEach((key, value) -> next.put(String.valueOf(key), value));
                    next.put("maxScore", clampInt(next.get("maxScore"), 1, 100));
                    next.put("score", clampInt(next.get("score"), 0, (Integer) next.get("maxScore")));
                    next.put("confidence", clampDouble(next.get("confidence"), 0.0, 1.0));
                    normalized.add(next);
                }
            }
            parsed.put("scores", normalized);
        }
    }

    private int clampInt(Object value, int min, int max) {
        int number = value instanceof Number n ? n.intValue() : min;
        return Math.max(min, Math.min(max, number));
    }

    private double clampDouble(Object value, double min, double max) {
        double number = value instanceof Number n ? n.doubleValue() : min;
        return Math.max(min, Math.min(max, number));
    }
}
```

- [ ] **Step 4: Wire parser into review service**

Inject `ReviewOutputParser` into `ReviewServiceImpl` and replace:

```java
Map<String, Object> parsed = parseModelOutput(modelText);
```

with:

```java
Map<String, Object> parsed = reviewOutputParser.parse(modelText);
```

Remove the private parsing helper methods from `ReviewServiceImpl` after the new parser tests pass.

- [ ] **Step 5: Run focused tests**

```powershell
mvn -q -Dtest=ReviewOutputParserTest,ReviewServiceImplTest test
```

Expected: exit code `0`.

- [ ] **Step 6: Commit parser extraction**

```powershell
git add src/main/java/com/lqr/paperragserver/review/assessment/ReviewOutputParser.java src/test/java/com/lqr/paperragserver/review/assessment/ReviewOutputParserTest.java src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java src/test/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImplTest.java
git commit -m "refactor: extract review output parser"
```

---

## Task 4: Reference Format Checker

**Files:**
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/risk/ReferenceFormatChecker.java`
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/risk/ReferenceFormatCheckerTest.java`

- [ ] **Step 1: Write failing checker test**

Create `ReferenceFormatCheckerTest.java`:

```java
package com.lqr.paperragserver.review.risk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceFormatCheckerTest {
    private final ReferenceFormatChecker checker = new ReferenceFormatChecker();

    @Test
    void checkShouldReportMissingYearAndOutdatedReferences() {
        var risks = checker.check("""
                [1] 王某某. 智能评审系统研究. 计算机应用, 2010.
                [2] Li X. Paper Review with AI. Conference on AI, 2011.
                [3] 张某某. 缺少年份的文献. 某某出版社.
                """);
        assertThat(risks).extracting(ReferenceFormatChecker.ReferenceRisk::riskType)
                .contains("REFERENCE_FORMAT", "REFERENCE_OUTDATED");
    }

    @Test
    void checkShouldReturnEmptyForBlankInput() {
        assertThat(checker.check("   ")).isEmpty();
    }
}
```

- [ ] **Step 2: Run failing test**

```powershell
mvn -q -Dtest=ReferenceFormatCheckerTest test
```

Expected: fails because `ReferenceFormatChecker` does not exist.

- [ ] **Step 3: Implement checker**

Create `ReferenceFormatChecker.java`:

```java
package com.lqr.paperragserver.review.risk;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ReferenceFormatChecker {
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern NUMBERED_REFERENCE = Pattern.compile("^\\s*(\\[\\d+]|\\d+[.)、])\\s*.+");

    public List<ReferenceRisk> check(String referencesText) {
        if (referencesText == null || referencesText.isBlank()) {
            return List.of();
        }
        List<String> entries = splitEntries(referencesText);
        List<ReferenceRisk> risks = new ArrayList<>();
        int recentCount = 0;
        int datedCount = 0;
        int currentYear = Year.now().getValue();
        for (String entry : entries) {
            var matcher = YEAR_PATTERN.matcher(entry);
            if (!matcher.find()) {
                risks.add(new ReferenceRisk("REFERENCE_FORMAT", "MEDIUM", entry, "参考文献条目缺少年份，建议补全出版年份。", 0.82));
                continue;
            }
            datedCount++;
            int year = Integer.parseInt(matcher.group(1));
            if (currentYear - year <= 5) {
                recentCount++;
            }
        }
        if (datedCount >= 2 && recentCount == 0) {
            risks.add(new ReferenceRisk("REFERENCE_OUTDATED", "LOW", referencesText.length() > 500 ? referencesText.substring(0, 500) : referencesText, "参考文献整体偏旧，建议补充近五年相关研究。", 0.76));
        }
        return risks;
    }

    private List<String> splitEntries(String referencesText) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : referencesText.split("\\R")) {
            if (NUMBERED_REFERENCE.matcher(line).matches()) {
                if (!current.isEmpty()) {
                    entries.add(current.toString().trim());
                }
                current = new StringBuilder(line.trim());
            } else if (!line.isBlank()) {
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(line.trim());
            }
        }
        if (!current.isEmpty()) {
            entries.add(current.toString().trim());
        }
        return entries;
    }

    public record ReferenceRisk(String riskType, String riskLevel, String evidence, String suggestion, double confidence) {
    }
}
```

- [ ] **Step 4: Run focused test and commit**

```powershell
mvn -q -Dtest=ReferenceFormatCheckerTest test
git add src/main/java/com/lqr/paperragserver/review/risk/ReferenceFormatChecker.java src/test/java/com/lqr/paperragserver/review/risk/ReferenceFormatCheckerTest.java
git commit -m "feat: add reference risk checker"
```

Expected: test and commit succeed.

---

## Task 5: Risk Item Persistence and API

**Files:**
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/entity/ReviewRiskItemEntity.java`
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/mapper/ReviewRiskItemMapper.java`
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewRiskItemResponse.java`
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/dto/ReviewRiskUpdateRequest.java`
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/risk/ReviewRiskService.java`
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/risk/ReviewRiskServiceTest.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/ReviewService.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/web/ReviewController.java`

- [ ] **Step 1: Write failing risk service test**

Create `ReviewRiskServiceTest.java`:

```java
package com.lqr.paperragserver.review.risk;

import com.lqr.paperragserver.review.entity.ReviewRiskItemEntity;
import com.lqr.paperragserver.review.mapper.ReviewRiskItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewRiskServiceTest {
    private final ReviewRiskItemMapper mapper = mock(ReviewRiskItemMapper.class);
    private final ReviewRiskService service = new ReviewRiskService(mapper);

    @Test
    void replaceReportRisksShouldPersistNormalizedItems() {
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        service.replaceReportRisks(reportId, taskId, List.of(Map.of("type", "REFERENCE_FORMAT", "level", "HIGH", "evidence", "[1] 缺少年份", "suggestion", "补全年份", "confidence", 0.9)));
        ArgumentCaptor<ReviewRiskItemEntity> captor = ArgumentCaptor.forClass(ReviewRiskItemEntity.class);
        verify(mapper).deleteByReportId(reportId);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRiskType()).isEqualTo("REFERENCE_FORMAT");
        assertThat(captor.getValue().getRiskLevel()).isEqualTo("HIGH");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void updateStatusShouldSetStatusAndReviewerNote() {
        UUID riskId = UUID.randomUUID();
        ReviewRiskItemEntity existing = new ReviewRiskItemEntity();
        existing.setId(riskId);
        when(mapper.selectById(riskId)).thenReturn(existing);
        service.updateStatus(riskId, "CONFIRMED", "证据明确");
        verify(mapper).updateById(any(ReviewRiskItemEntity.class));
        assertThat(existing.getStatus()).isEqualTo("CONFIRMED");
        assertThat(existing.getReviewerNote()).isEqualTo("证据明确");
    }
}
```

- [ ] **Step 2: Run failing test**

```powershell
mvn -q -Dtest=ReviewRiskServiceTest test
```

Expected: fails because risk classes do not exist.

- [ ] **Step 3: Implement risk entity and mapper**

Create `ReviewRiskItemEntity.java`:

```java
package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "public.review_risk_item", autoResultMap = true)
public class ReviewRiskItemEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    private UUID reportId;
    private UUID taskId;
    private String riskType;
    private String riskLevel;
    private String evidence;
    @TableField(value = "evidence_location", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> evidenceLocation;
    private String suggestion;
    private String detector;
    private BigDecimal confidence;
    private String status;
    private String reviewerNote;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

Create `ReviewRiskItemMapper.java`:

```java
package com.lqr.paperragserver.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.review.entity.ReviewRiskItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewRiskItemMapper extends BaseMapper<ReviewRiskItemEntity> {
    @Delete("delete from public.review_risk_item where report_id = #{reportId}")
    int deleteByReportId(@Param("reportId") UUID reportId);

    @Select("select * from public.review_risk_item where report_id = #{reportId} order by created_at asc")
    List<ReviewRiskItemEntity> selectByReportId(@Param("reportId") UUID reportId);
}
```

- [ ] **Step 4: Implement risk DTOs and service**

Create `ReviewRiskUpdateRequest.java`:

```java
package com.lqr.paperragserver.review.dto;

import jakarta.validation.constraints.Pattern;

public record ReviewRiskUpdateRequest(@Pattern(regexp = "OPEN|CONFIRMED|IGNORED|RESOLVED") String status, String reviewerNote) {
}
```

Create `ReviewRiskItemResponse.java` with a `from(ReviewRiskItemEntity entity)` factory returning all entity fields.

Create `ReviewRiskService.java` with these public methods:

```java
public List<ReviewRiskItemResponse> listByReportId(UUID reportId)
public void replaceReportRisks(UUID reportId, UUID taskId, Object risks)
public ReviewRiskItemResponse updateStatus(UUID riskId, String status, String reviewerNote)
```

Implementation details:

- `replaceReportRisks` first calls `riskItemMapper.deleteByReportId(reportId)`.
- It accepts only `List<?>` risks.
- Each map risk accepts either `type`/`level` or `riskType`/`riskLevel`.
- Default `detector` is `MODEL`.
- Default `status` is `OPEN`.
- Clamp numeric confidence to `[0, 1]` and store as `BigDecimal`.
- `updateStatus` loads by id, throws `ResponseStatusException(HttpStatus.NOT_FOUND, "风险项不存在")` if missing, then updates status/note/time.

- [ ] **Step 5: Add controller/service API**

In `ReviewService.java`, add:

```java
List<ReviewRiskItemResponse> listRisks(UUID currentUserId, boolean admin, UUID reportId);
ReviewRiskItemResponse updateRisk(UUID currentUserId, boolean admin, UUID riskId, ReviewRiskUpdateRequest request);
```

In `ReviewController.java`, add endpoints:

```http
GET /reviews/reports/{reportId}/risks
PUT /reviews/risks/{riskId}
POST /reviews/risks/{riskId}/confirm
POST /reviews/risks/{riskId}/ignore
POST /reviews/risks/{riskId}/resolve
```

Each endpoint must call `requireReviewer(principal)` and delegate to `reviewService`.

In `ReviewServiceImpl`, inject `ReviewRiskService`. After report insert/update in `generateAiReview`, call:

```java
reviewRiskService.replaceReportRisks(report.getId(), task.getId(), report.getRisks());
```

Implement `listRisks` by checking report existence and returning `reviewRiskService.listByReportId(reportId)`. Implement `updateRisk` by delegating to `reviewRiskService.updateStatus(...)`.

- [ ] **Step 6: Verify and commit**

```powershell
mvn -q -Dtest=ReviewRiskServiceTest test
mvn -q -DskipTests compile
git add src/main/java/com/lqr/paperragserver/review/entity/ReviewRiskItemEntity.java src/main/java/com/lqr/paperragserver/review/mapper/ReviewRiskItemMapper.java src/main/java/com/lqr/paperragserver/review/dto/ReviewRiskItemResponse.java src/main/java/com/lqr/paperragserver/review/dto/ReviewRiskUpdateRequest.java src/main/java/com/lqr/paperragserver/review/risk/ReviewRiskService.java src/test/java/com/lqr/paperragserver/review/risk/ReviewRiskServiceTest.java src/main/java/com/lqr/paperragserver/review/service/ReviewService.java src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java src/main/java/com/lqr/paperragserver/review/web/ReviewController.java
git commit -m "feat: persist review risk items"
```

Expected: tests, compile, and commit succeed.

---

## Task 6: Merge Reference Risks into Review Reports

**Files:**
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java`
- Modify: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImplTest.java`

- [ ] **Step 1: Add failing merge test**

In `ReviewServiceImplTest.java`, add a test that invokes a private helper by reflection:

```java
@Test
void mergeRisksShouldAppendReferenceRisksWithoutDroppingModelRisks() throws Exception {
    List<Map<String, Object>> modelRisks = List.of(Map.of(
            "type", "STRUCTURE_MISSING",
            "level", "LOW",
            "evidence", "缺少讨论章节",
            "suggestion", "补充讨论"
    ));
    List<ReferenceFormatChecker.ReferenceRisk> referenceRisks = List.of(
            new ReferenceFormatChecker.ReferenceRisk("REFERENCE_FORMAT", "MEDIUM", "[1] 缺少年份", "补全年份", 0.82)
    );

    Method method = ReviewServiceImpl.class.getDeclaredMethod("mergeRisks", Object.class, List.class);
    method.setAccessible(true);
    Object merged = method.invoke(service, modelRisks, referenceRisks);

    assertThat((List<?>) merged).hasSize(2);
}
```

- [ ] **Step 2: Run failing test**

```powershell
mvn -q -Dtest=ReviewServiceImplTest test
```

Expected: fails because `mergeRisks` does not exist.

- [ ] **Step 3: Inject checker and merge risks**

In `ReviewServiceImpl`, inject:

```java
private final ReferenceFormatChecker referenceFormatChecker;
```

After model output parsing in `generateAiReview`, compute:

```java
List<ReferenceFormatChecker.ReferenceRisk> referenceRisks = referenceFormatChecker.check(structuredReferences(parsed));
```

Replace setting risks with:

```java
report.setRisks(mergeRisks(valueOrDefault(parsed.get("risks"), List.of()), referenceRisks));
```

Add helpers:

```java
private String structuredReferences(Map<String, Object> parsed) {
    Object sections = parsed.get("paperSections");
    if (sections instanceof Map<?, ?> map) {
        Object references = map.get("references");
        return references == null ? "" : String.valueOf(references);
    }
    return "";
}

private Object mergeRisks(Object modelRisks, List<ReferenceFormatChecker.ReferenceRisk> referenceRisks) {
    List<Object> merged = new ArrayList<>();
    if (modelRisks instanceof List<?> list) {
        merged.addAll(list);
    }
    for (ReferenceFormatChecker.ReferenceRisk risk : referenceRisks) {
        merged.add(Map.of(
                "type", risk.riskType(),
                "level", risk.riskLevel(),
                "evidence", risk.evidence(),
                "suggestion", risk.suggestion(),
                "detector", "REFERENCE_RULE",
                "confidence", risk.confidence()
        ));
    }
    return merged;
}
```

- [ ] **Step 4: Verify and commit**

```powershell
mvn -q -Dtest=ReviewServiceImplTest,ReferenceFormatCheckerTest test
git add src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java src/test/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImplTest.java
git commit -m "feat: merge reference risks into review reports"
```

---

## Task 7: Audit Diff and Manual Delta

**Files:**
- Create: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/audit/ReviewAuditService.java`
- Create: `E:/Java_code/paper-rag-server/src/test/java/com/lqr/paperragserver/review/audit/ReviewAuditServiceTest.java`
- Modify: `E:/Java_code/paper-rag-server/src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java`

- [ ] **Step 1: Write failing audit test**

Create `ReviewAuditServiceTest.java`:

```java
package com.lqr.paperragserver.review.audit;

import com.lqr.paperragserver.review.entity.ReviewAuditLogEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewAuditServiceTest {
    private final ReviewAuditLogMapper mapper = mock(ReviewAuditLogMapper.class);
    private final ReviewAuditService service = new ReviewAuditService(mapper);

    @Test
    void appendShouldStoreBeforeAfterAndDiff() {
        service.append(UUID.randomUUID(), UUID.randomUUID(), "ADJUST_REPORT", "人工调整", Map.of("score", 70), Map.of("score", 80), Map.of("ip", "local"));
        ArgumentCaptor<ReviewAuditLogEntity> captor = ArgumentCaptor.forClass(ReviewAuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getBeforeSnapshot()).containsEntry("score", 70);
        assertThat(captor.getValue().getAfterSnapshot()).containsEntry("score", 80);
        assertThat(captor.getValue().getDiff()).containsKey("score");
    }
}
```

- [ ] **Step 2: Run failing test**

```powershell
mvn -q -Dtest=ReviewAuditServiceTest test
```

Expected: fails because `ReviewAuditService` does not exist.

- [ ] **Step 3: Implement audit service**

Create `ReviewAuditService.java` with `append(...)` that builds `ReviewAuditLogEntity`, sets `snapshot` to `afterSnapshot`, sets `beforeSnapshot`, `afterSnapshot`, `clientInfo`, and sets `diff` to a shallow map of changed keys:

```java
result.put(key, Map.of("before", beforeValue, "after", afterValue));
```

Use `OffsetDateTime.now()` and `UUID.randomUUID()`.

- [ ] **Step 4: Add manual delta in report update**

In `ReviewServiceImpl.updateReport`, before mutation:

```java
Map<String, Object> beforeSnapshot = reportSnapshot(report);
```

Before `reportMapper.updateById(report)`:

```java
report.setManualDelta(manualDelta(beforeSnapshot, reportSnapshot(report)));
```

Use helper:

```java
private Map<String, Object> manualDelta(Map<String, Object> before, Map<String, Object> after) {
    return Map.of(
            "scoreChanged", !Objects.equals(before.get("scores"), after.get("scores")) || !Objects.equals(before.get("totalScore"), after.get("totalScore")),
            "commentEdited", !Objects.equals(before.get("comments"), after.get("comments")),
            "riskOverridden", !Objects.equals(before.get("risks"), after.get("risks")),
            "finalRecommendationChanged", !Objects.equals(before.get("finalRecommendation"), after.get("finalRecommendation"))
    );
}
```

Inject `ReviewAuditService` and replace the update-report audit call with:

```java
reviewAuditService.append(task.getId(), currentUserId, "ADJUST_REPORT", "人工调整评审报告", beforeSnapshot, reportSnapshot(report), Map.of());
```

- [ ] **Step 5: Verify and commit**

```powershell
mvn -q -Dtest=ReviewAuditServiceTest test
mvn -q -DskipTests compile
git add src/main/java/com/lqr/paperragserver/review/audit/ReviewAuditService.java src/test/java/com/lqr/paperragserver/review/audit/ReviewAuditServiceTest.java src/main/java/com/lqr/paperragserver/review/service/impl/ReviewServiceImpl.java
git commit -m "feat: record review audit diffs"
```

---

## Task 8: Frontend Types and Risk API

**Files:**
- Modify: `E:/Java_code/paper-rag-server/frontend/src/types/index.ts`
- Modify: `E:/Java_code/paper-rag-server/frontend/src/api/reviews.ts`

- [ ] **Step 1: Extend TypeScript types**

Add near review types in `frontend/src/types/index.ts`:

```ts
export interface ReviewScoringRule {
  level: string;
  range: [number, number];
  description: string;
}

export interface ReviewRiskRecord {
  id: string;
  reportId: string;
  taskId: string;
  riskType: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  evidence: string | null;
  evidenceLocation: Record<string, unknown>;
  suggestion: string | null;
  detector: string | null;
  confidence: number | null;
  status: 'OPEN' | 'CONFIRMED' | 'IGNORED' | 'RESOLVED' | string;
  reviewerNote: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateReviewRiskPayload {
  status: 'OPEN' | 'CONFIRMED' | 'IGNORED' | 'RESOLVED';
  reviewerNote?: string | null;
}
```

Extend `ReviewCriterion` with `version`, `category`, `evidenceRequired`, `scoringRules`. Extend `ReviewReport` with `criterionVersion`, `modelVersion`, `promptVersion`, `confidence`, `manualDelta`.

- [ ] **Step 2: Add risk API functions**

In `frontend/src/api/reviews.ts`, add:

```ts
export async function listReviewRisks(reportId: string) {
  const { data } = await http.get<ReviewRiskRecord[]>(`/reviews/reports/${reportId}/risks`);
  return data;
}

export async function updateReviewRisk(riskId: string, payload: UpdateReviewRiskPayload) {
  const { data } = await http.put<ReviewRiskRecord>(`/reviews/risks/${riskId}`, payload);
  return data;
}

export async function confirmReviewRisk(riskId: string) {
  const { data } = await http.post<ReviewRiskRecord>(`/reviews/risks/${riskId}/confirm`);
  return data;
}

export async function ignoreReviewRisk(riskId: string) {
  const { data } = await http.post<ReviewRiskRecord>(`/reviews/risks/${riskId}/ignore`);
  return data;
}

export async function resolveReviewRisk(riskId: string) {
  const { data } = await http.post<ReviewRiskRecord>(`/reviews/risks/${riskId}/resolve`);
  return data;
}
```

- [ ] **Step 3: Build and commit**

```powershell
cd frontend
npm run build
cd ..
git add frontend/src/types/index.ts frontend/src/api/reviews.ts
git commit -m "feat: add review risk frontend api"
```

---

## Task 9: Tabbed Reviewer UI

**Files:**
- Modify: `E:/Java_code/paper-rag-server/frontend/src/composables/useReviews.ts`
- Modify: `E:/Java_code/paper-rag-server/frontend/src/views/review/ReviewWorkspaceView.vue`

- [ ] **Step 1: Add risk state to `useReviews.ts`**

Add refs:

```ts
const riskRecords = ref<ReviewRiskRecord[]>([]);
const riskLoading = ref(false);
```

Add methods:

```ts
async function loadRisks(reportId: string | null) {
  riskRecords.value = [];
  if (!reportId) return;
  riskLoading.value = true;
  try {
    riskRecords.value = await listReviewRisks(reportId);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    riskLoading.value = false;
  }
}

async function setRiskStatus(riskId: string, status: 'CONFIRMED' | 'IGNORED' | 'RESOLVED' | 'OPEN', reviewerNote?: string) {
  try {
    const updated = await updateReviewRisk(riskId, { status, reviewerNote: reviewerNote ?? null });
    riskRecords.value = riskRecords.value.map((item) => (item.id === riskId ? updated : item));
    ElMessage.success('风险状态已更新');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}
```

Call `loadRisks(selectedTask.value.report?.id ?? null)` after selecting a task and `loadRisks(report.id)` after AI report generation.

- [ ] **Step 2: Convert detail area to tabs**

In `ReviewWorkspaceView.vue`, wrap detail subsections in:

```vue
<el-tabs model-value="parse" class="review-tabs">
  <el-tab-pane label="结构化解析" name="parse"></el-tab-pane>
  <el-tab-pane label="多维评分" name="scores"></el-tab-pane>
  <el-tab-pane label="风险预警" name="risks"></el-tab-pane>
  <el-tab-pane label="评语意见" name="comments"></el-tab-pane>
  <el-tab-pane label="留档信息" name="audit"></el-tab-pane>
</el-tabs>
```

Move existing sections into the appropriate panes. Do not duplicate existing score/comment logic.

- [ ] **Step 3: Add normalized risk UI**

Inside the risks pane, render `reviews.riskRecords.value` with buttons that call `reviews.setRiskStatus(risk.id, 'CONFIRMED')`, `IGNORED`, and `RESOLVED`.

- [ ] **Step 4: Add audit metadata UI**

Inside the audit pane, show `selectedReport.modelVersion`, `promptVersion`, `criterionVersion`, `confidence`, and formatted `manualDelta` JSON.

- [ ] **Step 5: Build and commit**

```powershell
cd frontend
npm run build
cd ..
git add frontend/src/composables/useReviews.ts frontend/src/views/review/ReviewWorkspaceView.vue
git commit -m "feat: show review risks in tabbed workspace"
```

---

## Task 10: Full Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Run focused backend tests**

```powershell
mvn -q -Dtest=ReviewOutputParserTest,ReferenceFormatCheckerTest,ReviewRiskServiceTest,ReviewAuditServiceTest,ReviewServiceImplTest test
```

Expected: exit code `0`.

- [ ] **Step 2: Run backend full test suite**

```powershell
mvn test
```

Expected: exit code `0`. If unrelated pre-existing tests fail, record exact failing tests and do not claim full backend pass.

- [ ] **Step 3: Run frontend build**

```powershell
cd frontend
npm run build
cd ..
```

Expected: exit code `0`.

- [ ] **Step 4: Check working tree**

```powershell
git status --short
```

Expected: only user/pre-existing unrelated changes remain. At plan creation time these were `.env.example` staged and `.gitignore` modified.

---

## Self-Review

### Spec coverage

- Structure parsing metadata: Tasks 1–2.
- Configurable scoring rules: Tasks 1–2 and 8.
- AI JSON stability: Task 3.
- Reference risk detection: Tasks 4 and 6.
- Risk persistence and state changes: Tasks 5 and 9.
- Expert adjustment traceability: Task 7 and audit pane in Task 9.
- Reviewer UI phase-one tabs: Tasks 8–9.
- Verification: Task 10.

### Type consistency

- Backend and frontend risk statuses match: `OPEN`, `CONFIRMED`, `IGNORED`, `RESOLVED`.
- Backend and frontend risk levels match: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Backend response fields are Java record camelCase and match TypeScript interfaces.
- `scoringRules` remains JSON-compatible on both sides.
- Report metadata field names match between `ReviewReportResponse` and `ReviewReport`.

### Placeholder scan result

No task uses open-ended implementation placeholders. Each task lists concrete files, code shape, commands, and expected results.
