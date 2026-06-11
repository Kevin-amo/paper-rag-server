import type { MessageRole } from './auth';
import type { AnswerCitation } from './review';

export interface LiteratureSearchResult {
  title: string | null;
  authors: string[];
  abstractText: string | null;
  year: number | null;
  publishedDate: string | null;
  updatedDate: string | null;
  categories: string[];
  primaryCategory: string | null;
  doi: string | null;
  url: string | null;
  pdfUrl: string | null;
  source: string;
  externalId: string | null;
}

export interface LiteratureSearchMessageMetadata {
  type: 'LITERATURE_SEARCH_RESULT';
  query: string;
  params: {
    limit: number | null;
    dateFrom: string | null;
    sortBy: 'relevance' | 'date' | null;
    categories: string[];
  };
  items: LiteratureSearchResult[];
}

export interface AgentStepTrace {
  index: number;
  thoughtSummary: string;
  action: string;
  actionInput: Record<string, unknown>;
  observationSummary: string;
}

export interface AgentResultMetadata {
  type: 'AGENT_RESULT';
  agent: string;
  steps: AgentStepTrace[];
  literature?: LiteratureSearchMessageMetadata;
  localPaperChunks?: Array<Record<string, unknown>>;
  stopReason?: string;
}

export interface AgentAskPayload {
  conversationId?: string;
  question: string;
  topK?: number;
}

export interface AgentStreamEvent {
  type: 'start' | 'step' | 'thought' | 'tool_call' | 'tool_result' | 'delta' | 'done' | 'error';
  conversationId: string | null;
  step: number | null;
  thought: string | null;
  toolName: string | null;
  toolInput: Record<string, unknown>;
  observation: string | null;
  delta: string | null;
  answer: string | null;
  citations: AnswerCitation[];
  metadata: AgentResultMetadata | Record<string, unknown>;
  message: string | null;
}

export type ConversationMessageMetadata = Record<string, unknown> | LiteratureSearchMessageMetadata | AgentResultMetadata | null;

export interface Conversation {
  id: string;
  ownerUserId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: string;
  conversationId: string;
  role: MessageRole;
  messageOrder: number;
  content: string;
  citations: AnswerCitation[];
  metadata: ConversationMessageMetadata;
  createdAt: string;
  streaming?: boolean;
}

export interface CreateConversationPayload {
  title?: string;
}

export interface UpdateConversationPayload {
  title: string;
}
