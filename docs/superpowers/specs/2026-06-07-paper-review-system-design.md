# 科研论文智能评审系统落地设计规格

日期：2026-06-07
项目：paper-rag-server
范围：基于现有 Spring Boot + Vue 项目的真实开发实施方案，覆盖全部目标能力并分期实现。

## 1. 背景与目标

本系统面向科研论文辅助评审场景，目标是在保留专家评审核心价值的前提下，通过国产大模型、文档解析、规则检测、结构化评分和协同流程提升评审效率、一致性与可追溯性。

当前项目已经具备以下基础能力：

- 后端：Spring Boot 3、Java 21、Spring Security、MyBatis-Plus、PostgreSQL/pgvector、Redis、RabbitMQ、Apache Tika、PDFBox。
- 前端：Vue 3、Vite、Element Plus。
- AI 链路：DashScope / Qwen，用于问答、Embedding、Rerank、论文结构化解析和辅助评审。
- 已有业务基础：论文上传、异步入库、结构化解析、评审任务、评审指标、AI 评审报告、人工调整、审计留档、角色管理。

本规格以现有代码为基线，避免另起炉灶，采用“全量设计、分期实现”的方式落地。

## 2. 总体架构与分期边界

### 2.1 后端子域

系统按以下后端子域演进：

1. `document`
   - 负责 PDF/Word 上传、解析、切分、入库、全文与元数据保存。

2. `document.structured`
   - 负责论文结构化解析：章节识别、关键信息提取、字段置信度、缺失字段检测。

3. `review.rubric`
   - 负责评审维度、权重、评分细则、等级描述、启停与版本管理。

4. `review.assessment`
   - 负责 AI 评分建议、综合分、结构化评语、自然语言评语、专家调整。

5. `review.risk`
   - 负责政治不当表述、参考文献规范、结构缺失、语言质量等风险检测。风险项必须带证据片段和处理建议。

6. `review.collaboration`
   - 负责多专家并行评审、任务分配、意见汇总、共识状态、进度跟踪。

7. `review.audit`
   - 负责所有评审过程、专家修改、状态流转和最终结果的可追溯留档。

### 2.2 分期边界

#### 一期：单专家智能评审闭环

目标是形成可演示、可测试、可留档的智能评审闭环：

- 增强结构化解析结果。
- 扩展评分细则配置。
- 增加风险/参考文献检查结果结构。
- 支持 AI 建议 + 专家人工修改 + 审计留档。
- 保留多人协同所需的扩展点。

#### 二期：管理后台与多人协同

- 评审标准可视化配置。
- 多专家分配与并行评审。
- 意见汇总、共识形成、进度跟踪。
- 管理员查看全局任务状态。

#### 三期：评测、治理与安全增强

- 建立人工标注评测集。
- 计算解析准确率、风险提示准确率、评分误差。
- 增加模型版本、提示词版本、报告版本追溯。
- 加强数据脱敏、访问控制、知识产权保护。

## 3. 数据库模型设计

当前库已经包含：

- `paper_document`
- `paper_structured_parse`
- `review_criterion`
- `review_task`
- `review_report`
- `review_audit_log`

数据库演进策略是扩展现有表，并新增少量明细表。

### 3.1 结构化解析表增强

保留 `paper_structured_parse`，新增：

```sql
alter table public.paper_structured_parse
  add column if not exists parser_version varchar(64),
  add column if not exists model_version varchar(128),
  add column if not exists prompt_version varchar(64),
  add column if not exists quality_metrics jsonb not null default '{}'::jsonb;
```

`quality_metrics` 示例：

```json
{
  "sectionCoverage": 0.92,
  "keyFieldCoverage": 0.88,
  "evidenceCoverage": 0.81,
  "parseConfidence": 0.86
}
```

用途：

- 支持解析准确率评测统计。
- 支持字段级低置信度提示。
- 支持模型/提示词版本追溯。

### 3.2 评分指标表增强

保留 `review_criterion`，新增：

```sql
alter table public.review_criterion
  add column if not exists version int not null default 1,
  add column if not exists scoring_rules jsonb not null default '[]'::jsonb,
  add column if not exists evidence_required boolean not null default true,
  add column if not exists category varchar(64);
```

`scoring_rules` 示例：

```json
[
  {
    "level": "excellent",
    "range": [90, 100],
    "description": "研究问题明确，方法与数据支撑充分，结论可信"
  },
  {
    "level": "good",
    "range": [75, 89],
    "description": "整体符合要求，但部分论证或表达仍需加强"
  }
]
```

用途：

- 管理员可配置每个维度的评分细则。
- AI 评分必须引用细则和证据。
- 后续可实现评分标准版本化。

### 3.3 评审报告表增强

保留 `review_report`，新增：

```sql
alter table public.review_report
  add column if not exists criterion_version int,
  add column if not exists model_version varchar(128),
  add column if not exists prompt_version varchar(64),
  add column if not exists confidence numeric(5,4),
  add column if not exists manual_delta jsonb not null default '{}'::jsonb;
```

`manual_delta` 示例：

```json
{
  "scoreChanged": true,
  "changedCriteria": ["INNOVATION", "LOGIC"],
  "commentEdited": true,
  "riskOverridden": false
}
```

用途：

- 记录 AI 建议与专家修改差异。
- 为评分误差评测提供数据基础。
- 支持后续模型效果评估。

### 3.4 新增风险明细表

当前 `review_report.risks` 是 JSON 数组，适合展示，但不利于筛选、统计和状态流转。建议新增：

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
  constraint chk_review_risk_status check (status in ('OPEN', 'CONFIRMED', 'IGNORED', 'RESOLVED'))
);
```

风险类型建议：

- `POLITICAL_EXPRESSION`
- `REFERENCE_FORMAT`
- `REFERENCE_OUTDATED`
- `CITATION_MISMATCH`
- `STRUCTURE_MISSING`
- `LANGUAGE_QUALITY`
- `ACADEMIC_INTEGRITY_HINT`

### 3.5 新增多人协同表

二期新增 `review_assignment`：

```sql
create table if not exists public.review_assignment (
  id uuid primary key default uuid_generate_v4(),
  task_id uuid not null references public.review_task(id) on delete cascade,
  reviewer_user_id uuid not null references public.sys_user(id) on delete cascade,
  role varchar(32) not null default 'REVIEWER',
  status varchar(32) not null default 'ASSIGNED',
  assigned_at timestamptz not null default now(),
  due_at timestamptz,
  submitted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_review_assignment_task_reviewer unique (task_id, reviewer_user_id),
  constraint chk_review_assignment_role check (role in ('LEAD', 'REVIEWER', 'ARBITER')),
  constraint chk_review_assignment_status check (status in ('ASSIGNED', 'REVIEWING', 'SUBMITTED', 'RETURNED', 'CANCELLED'))
);
```

二期新增 `review_consensus`：

```sql
create table if not exists public.review_consensus (
  id uuid primary key default uuid_generate_v4(),
  task_id uuid not null references public.review_task(id) on delete cascade,
  lead_reviewer_user_id uuid references public.sys_user(id) on delete set null,
  score_summary jsonb not null default '{}'::jsonb,
  comment_summary jsonb not null default '{}'::jsonb,
  disagreement_items jsonb not null default '[]'::jsonb,
  final_score int,
  final_recommendation text,
  status varchar(32) not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint chk_review_consensus_score check (final_score is null or final_score between 0 and 100),
  constraint chk_review_consensus_status check (status in ('DRAFT', 'IN_DISCUSSION', 'CONFIRMED', 'ARCHIVED'))
);
```

### 3.6 审计留档增强

保留 `review_audit_log`，建议新增：

```sql
alter table public.review_audit_log
  add column if not exists before_snapshot jsonb not null default '{}'::jsonb,
  add column if not exists after_snapshot jsonb not null default '{}'::jsonb,
  add column if not exists diff jsonb not null default '{}'::jsonb,
  add column if not exists client_info jsonb not null default '{}'::jsonb;
```

用途：

- 支持专家修改前后对比。
- 支持追溯评分、评语、风险项变更。
- 满足完整数据留档和可追溯管理。

## 4. 后端服务/API 设计

原则：保留现有接口语义，按子域渐进扩展。当前已有 `ReviewController`、`ReviewServiceImpl`、`PaperStructuredParseService`，后续建议拆分服务，避免单个类承担过多职责。

### 4.1 后端模块拆分

建议新增/调整包结构：

```text
com.lqr.paperragserver.review
├── application
│   └── ReviewApplicationService.java
├── rubric
│   ├── ReviewRubricService.java
│   └── RubricScoringPolicy.java
├── assessment
│   ├── ReviewAssessmentService.java
│   ├── ReviewPromptFactory.java
│   └── ReviewOutputParser.java
├── risk
│   ├── ReviewRiskService.java
│   ├── PoliticalExpressionDetector.java
│   ├── ReferenceFormatChecker.java
│   └── RiskEvidenceExtractor.java
├── collaboration
│   ├── ReviewAssignmentService.java
│   ├── ReviewConsensusService.java
│   └── ReviewProgressService.java
└── audit
    └── ReviewAuditService.java
```

现有 `ReviewServiceImpl` 可逐步收缩为 facade 或废弃。

### 4.2 评审任务 API

保留：

```http
GET /reviews/tasks
GET /reviews/tasks/{taskId}
POST /reviews/tasks
```

增强查询参数：

```http
GET /reviews/tasks?status=&keyword=&reviewerId=&submitterId=&dueBefore=&page=&size=
```

响应中新增进度信息：

```json
{
  "progress": {
    "parseStatus": "COMPLETED",
    "aiReportStatus": "AI_GENERATED",
    "riskOpenCount": 2,
    "assignmentSubmittedCount": 1,
    "assignmentTotalCount": 3
  }
}
```

### 4.3 AI 评审报告 API

保留：

```http
POST /reviews/tasks/{taskId}/ai-report
PUT /reviews/reports/{reportId}
```

新增：

```http
POST /reviews/tasks/{taskId}/ai-report/regenerate
GET /reviews/reports/{reportId}/versions
GET /reviews/reports/{reportId}/audit-logs
```

AI 生成流程：

1. 读取 `paper_structured_parse.merged_result`。
2. 读取启用的 `review_criterion` 和 `scoring_rules`。
3. 构建分维度 prompt。
4. 调用 Qwen/DashScope。
5. 解析 JSON 输出。
6. 计算综合分。
7. 持久化 `review_report`。
8. 拆出 `review_risk_item`。
9. 记录 `review_audit_log`。

### 4.4 评分标准 API

现有：

```http
GET /reviews/criteria
POST /reviews/criteria
PUT /reviews/criteria/{id}
```

增强：

```http
POST /reviews/criteria/{id}/enable
POST /reviews/criteria/{id}/disable
POST /reviews/criteria/{id}/clone-version
GET /reviews/criteria/active-version
```

`ReviewCriterionRequest` 增加：

```json
{
  "code": "INNOVATION",
  "name": "研究创新性",
  "description": "评价选题、方法、数据和结论的新颖程度",
  "maxScore": 100,
  "weight": 20,
  "category": "CORE",
  "evidenceRequired": true,
  "scoringRules": [
    {
      "level": "excellent",
      "range": [90, 100],
      "description": "创新点清晰且有充分证据支撑"
    }
  ],
  "enabled": true,
  "sortOrder": 30
}
```

### 4.5 风险预警 API

新增：

```http
GET /reviews/reports/{reportId}/risks
POST /reviews/reports/{reportId}/risks/recheck
PUT /reviews/risks/{riskId}
POST /reviews/risks/{riskId}/confirm
POST /reviews/risks/{riskId}/ignore
POST /reviews/risks/{riskId}/resolve
```

风险项响应：

```json
{
  "id": "uuid",
  "riskType": "REFERENCE_FORMAT",
  "riskLevel": "MEDIUM",
  "evidence": "[12] 王某某. ...",
  "evidenceLocation": {
    "section": "references",
    "page": 8,
    "startOffset": 10231,
    "endOffset": 10320
  },
  "suggestion": "建议补全年份、期刊卷期和页码",
  "confidence": 0.86,
  "status": "OPEN"
}
```

### 4.6 参考文献规范检查

采用“规则 + 模型复核”：

1. 从结构化解析中的 `references` 提取参考文献块。
2. 用规则分割条目。
3. 检查编号、年份、作者、题名、出版源、页码等关键字段。
4. 检查近 5 年文献占比。
5. 粗略匹配正文引用与参考文献条目。
6. 对不确定项交给模型复核。
7. 输出 `review_risk_item`。

### 4.7 政治不当表述检测

采用三层机制：

1. 规则词典初筛。
2. 模型结合上下文判断是否属于学术引用、客观描述、批判分析或不当表达。
3. 专家确认风险状态。

### 4.8 多人协同 API（二期）

新增：

```http
POST /reviews/tasks/{taskId}/assignments
GET /reviews/tasks/{taskId}/assignments
PUT /reviews/assignments/{assignmentId}
POST /reviews/assignments/{assignmentId}/submit
GET /reviews/tasks/{taskId}/consensus
POST /reviews/tasks/{taskId}/consensus/generate
PUT /reviews/consensus/{consensusId}
POST /reviews/consensus/{consensusId}/confirm
```

协同流程：

1. 管理员或组长分配 2-3 名专家。
2. 每位专家独立查看 AI 建议并提交个人评审。
3. 系统汇总分数差异、评语差异、风险确认差异。
4. 组长生成共识草案。
5. 组长确认最终意见。
6. 所有步骤进入 `review_audit_log`。

### 4.9 进度跟踪 API

新增：

```http
GET /reviews/progress/overview
GET /reviews/progress/tasks
GET /reviews/tasks/{taskId}/timeline
```

时间线事件示例：

```json
[
  {
    "time": "2026-06-07T10:00:00Z",
    "action": "UPLOAD",
    "operator": "teacher-a",
    "note": "上传论文"
  },
  {
    "time": "2026-06-07T10:03:00Z",
    "action": "AI_REVIEW",
    "operator": "reviewer-a",
    "note": "生成 AI 辅助评审报告"
  }
]
```

## 5. 前端页面与交互设计

当前前端已有 `/review` 评审工作台。建议保留页面入口，将右侧详情区拆成多 Tab。

### 5.1 评审专家工作台

建议结构：

```text
ReviewWorkspaceView
├── ReviewTaskPanel
├── ReviewDetailHeader
├── ReviewStructuredParsePanel
├── ReviewScorePanel
├── ReviewRiskPanel
├── ReviewCommentPanel
├── ReviewAuditTimeline
└── ReviewCollaborationPanel
```

#### Tab 1：结构化解析

展示：标题、摘要、引言、文献综述、方法、实验结果、讨论、结论、关键词、研究对象、研究问题、创新点、方法路径、实验数据摘要、主要结论、字段置信度、缺失字段、低置信度字段。

交互：重新解析、展开/折叠长文本、低置信度字段高亮、显示证据片段。

#### Tab 2：多维评分

展示：维度名称、权重、评分细则、AI 建议分、AI 理由、置信度、专家调整分、专家调整说明。

交互：调整分数、自动计算总分、标记与 AI 建议差异、保存为 `ADJUSTED`、确认为 `CONFIRMED` 或 `COMPLETED`。

#### Tab 3：风险预警

展示：风险类型、风险等级、证据片段、证据位置、检测来源、置信度、建议、状态。

操作：确认风险、忽略风险、标记已解决、添加专家备注、重新检测风险。

#### Tab 4：评语与最终意见

包含结构化评语和自然语言评语。专家可编辑 AI 初稿，并可根据调整后的评分重新生成。

#### Tab 5：时间线/留档

展示上传、入库、结构化解析、AI 评审、风险检测、专家修改、确认最终意见、协同提交/共识确认等事件。

### 5.2 管理员后台

建议新增后台二级页面：

```text
/admin/users
/admin/review-criteria
/admin/review-assignments
/admin/review-progress
/admin/review-quality
```

功能：

- 评审标准配置：维度、权重、评分等级、启停、版本。
- 协同分配：论文、专家、角色、截止时间。
- 进度看板：待处理、逾期、高风险、已完成。
- 质量评测：解析准确率、风险命中率、评分偏差、模型版本对比。

### 5.3 前端状态管理

建议将 `useReviews.ts` 拆分：

```text
composables/review/
├── useReviewTasks.ts
├── useReviewReport.ts
├── useReviewCriteria.ts
├── useReviewRisks.ts
├── useReviewAssignments.ts
├── useReviewProgress.ts
└── useStructuredParse.ts
```

## 6. AI/模型、提示词与准确率评测设计

当前项目使用 DashScope / Qwen，符合优先采用国产大模型的要求。建议继续保留该链路，重点增强 prompt 结构、JSON 输出稳定性、证据约束、规则检测、版本追踪和评测集。

### 6.1 模型任务拆分

不要让一个 prompt 同时完成所有任务，建议拆成：

1. 结构化字段补全。
2. 多维评分。
3. 评语生成。
4. 风险复核。

### 6.2 Prompt 设计原则

每个 prompt 应包含：

- 角色约束。
- 输入范围约束。
- 评分/判断标准。
- 证据要求。
- 严格 JSON Schema。
- 失败策略。

### 6.3 JSON 输出稳定性

建议新增：

```text
ReviewOutputParser
├── extractJson()
├── validateSchema()
├── normalizeScores()
├── normalizeRisks()
└── fallbackOnInvalid()
```

后端负责白名单字段、枚举校验、分数范围归一化、缺失字段默认值、原始输出保存。

### 6.4 风险检测策略

#### 政治不当表述

采用规则初筛、模型上下文复核、专家确认闭环。

#### 参考文献规范

规则优先：文献条目分割、年份检测、关键字段缺失检测、正文引用匹配、近 5 年文献占比统计，模型只复核疑难项。

#### 语言质量

采用长句、重复句、口语化表达、错别字和标点规则初筛，模型给出改写建议。

### 6.5 评测集与指标

原始指标需通过人工标注评测集验收。

#### 结构化解析

```text
accuracy = 正确字段数 / 总字段数
coverage = 非空字段数 / 应提取字段数
```

章节级可采用字符区间重叠率。

#### 风险提示

```text
precision = 正确风险提示数 / 系统风险提示数
recall = 正确风险提示数 / 人工标注风险数
```

建议将“风险提示准确率 98%”定义为高风险项 precision >= 98%。

#### 评分误差

```text
AI_MAE = mean(abs(ai_score - human_mean_score))
target = AI_MAE <= human_std * 0.2
```

同时统计每个维度 MAE、总分 MAE、专家修改率、AI 建议采纳率。

### 6.6 模型治理

每份报告必须记录：

- `model_version`
- `prompt_version`
- `criterion_version`
- `parser_version`
- `raw_model_output`
- `confidence`
- `generated_at`
- `adjusted_at`

## 7. 测试、性能、安全与验收设计

### 7.1 功能测试

后端覆盖：

- PDF/Word 上传后自动触发解析。
- 标准章节识别。
- 缺失章节识别。
- 低置信度字段标记。
- 重新解析覆盖旧结果。
- 启用/停用评分维度。
- 权重配置。
- 评分细则保存。
- AI 评分覆盖全部启用维度。
- 专家调整后总分重新计算。
- 政治风险候选识别。
- 参考文献格式错误识别。
- 文献过时提示。
- 风险确认/忽略/解决状态流转。
- 上传论文创建任务。
- 生成 AI 报告。
- 保存人工修改。
- 确认最终结果。
- 审计日志记录 before/after/diff。
- 多专家分配、独立提交、汇总差异、共识确认（二期）。

前端覆盖：

- 任务池筛选分页。
- 结构化解析展示。
- 评分调整。
- 风险状态变更。
- 评语编辑保存。
- 管理员配置评分标准。
- 协同分配与进度看板。

### 7.2 性能测试

建议指标：

```text
单篇 20 页 PDF：
- 上传响应：<= 2s 返回任务
- 文档解析入库：<= 60s
- 结构化解析：<= 90s
- AI 评审报告生成：<= 120s
- 前端任务列表加载：<= 1s
- 报告详情加载：<= 2s
```

分期并发目标：

```text
一期：
- 10 个评审用户并发
- 50 篇论文批量入库
- 1000 条评审任务查询不卡顿

二期：
- 50 个评审用户并发
- 500 篇论文任务池
- 多专家进度看板 3s 内返回

三期：
- 评测集批处理
- 模型调用失败重试
- 队列削峰
```

### 7.3 数据安全与知识产权保护

必须设计：

1. 权限隔离。
2. 上传文件类型和大小限制。
3. 原始文件与解析文本分开存储。
4. 模型调用日志脱敏。
5. `raw_model_output` 仅授权可见。
6. 可选调用模型前脱敏。
7. 审计追踪评分、评语、风险状态变更。
8. 评审论文和普通 RAG 文档检索隔离。
9. 默认禁止跨用户检索。
10. 明确数据留存和删除策略。

### 7.4 验收指标

| 指标 | 验收方式 |
|---|---|
| 解析准确率 >= 95% | 人工标注评测集，按字段级准确率统计 |
| 风险提示准确率 >= 98% | 对高风险项统计 precision |
| 评分误差 <= 人工标准差 20% | AI 分数与多专家均值比较 |
| 报告可追溯 | 抽查报告版本、模型版本、人工修改 diff |
| 数据安全 | 权限越权测试、日志脱敏检查 |
| 系统稳定性 | 批量上传、并发评审、模型失败重试测试 |

### 7.5 推荐测试结构

后端：

```text
src/test/java/.../document/structured
src/test/java/.../review/rubric
src/test/java/.../review/assessment
src/test/java/.../review/risk
src/test/java/.../review/collaboration
src/test/java/.../review/audit
```

前端：

```text
frontend/src/views/review/__tests__
frontend/src/components/review/__tests__
frontend/src/composables/review/__tests__
```

## 8. 后续实施建议

设计文档确认后，进入 implementation plan，建议按以下里程碑拆分：

1. 数据库迁移和 DTO 扩展。
2. 评分细则后端与前端配置。
3. 风险明细表与风险状态流转。
4. AI 输出解析器和 prompt 拆分。
5. 评审工作台 Tab 化重构。
6. 审计 diff 增强。
7. 多专家协同表与 API。
8. 管理后台进度与质量页面。
9. 评测集和质量指标计算。

## 9. 自审结论

- 本规格基于现有代码结构和表结构演进，没有要求重写项目。
- 原始需求的所有核心模块均已覆盖。
- 分期边界明确，一期可形成单专家智能评审闭环，二期扩展多人协同，三期完善评测与治理。
- 指标均给出可验收口径，避免仅在文档中承诺不可验证的准确率。
