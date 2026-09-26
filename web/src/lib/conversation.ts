const CID_KEY = 'rag_conversation_id';

/** 当前会话 ID（登录用户），localStorage 持久化 → 刷新页面后可恢复历史 */
export function getStoredConversationId(): string | null {
  return localStorage.getItem(CID_KEY);
}

export function storeConversationId(id: string | null): void {
  if (id) localStorage.setItem(CID_KEY, id);
  else localStorage.removeItem(CID_KEY);
}
