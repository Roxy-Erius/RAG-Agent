import { SSEEvent } from '../types';

/**
 * SSE 客户端：fetch + ReadableStream，按 "\n\n" 切分帧，逐帧 JSON.parse
 * 后端用 Spring SseEmitter，每个 data: 行是一段 JSON（见 docs/开发复盘.md §4.1）
 */
export async function sseChat(
  params: { message: string; sessionId?: string; conversationId?: string; token?: string },
  onEvent: (e: SSEEvent) => void,
  onError?: (err: Error) => void
): Promise<void> {
  const base = (import.meta.env.VITE_API_BASE || '/api');
  const url = new URL(`${window.location.origin}${base}/chat/stream`);
  if (params.message) url.searchParams.set('message', params.message);
  if (params.sessionId) url.searchParams.set('sessionId', params.sessionId);
  if (params.conversationId) url.searchParams.set('conversationId', params.conversationId);

  const headers: Record<string, string> = { Accept: 'text/event-stream' };
  if (params.token) headers.Authorization = `Bearer ${params.token}`;

  try {
    const res = await fetch(url.toString(), { method: 'GET', headers });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`SSE ${res.status}: ${text}`);
    }
    const reader = res.body!.getReader();
    const decoder = new TextDecoder('utf-8');
    let buf = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });

      let idx: number;
      // 一个 SSE 消息可能跨多个 chunk，用 \n\n 分隔帧；帧内 data: 行
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const frame = buf.slice(0, idx);
        buf = buf.slice(idx + 2);
        // 一个 frame 可能有多行 data:，逐行解析
        for (const line of frame.split('\n')) {
          if (!line.startsWith('data:')) continue;
          const jsonStr = line.slice(5).trim();
          if (!jsonStr) continue;
          try {
            const evt = JSON.parse(jsonStr) as SSEEvent;
            onEvent(evt);
            if (evt.type === 'done') return;
          } catch (e) {
            console.warn('SSE 解析失败:', jsonStr, e);
          }
        }
      }
    }
  } catch (err) {
    onError?.(err instanceof Error ? err : new Error(String(err)));
  }
}
