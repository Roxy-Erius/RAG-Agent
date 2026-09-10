const SESSION_KEY = 'rag_session_id';

/** 匿名会话 ID：localStorage 持久化 */
export function getSessionId(): string {
  let id = localStorage.getItem(SESSION_KEY);
  if (!id) {
    id = 'web_' + crypto.randomUUID().slice(0, 12);
    localStorage.setItem(SESSION_KEY, id);
  }
  return id;
}

/** 登录后可换绑到用户（先留接口） */
export function resetSessionId(): string {
  localStorage.removeItem(SESSION_KEY);
  return getSessionId();
}
