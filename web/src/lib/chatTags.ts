/**
 * 去掉消息里的结构化标签。
 * 历史消息落库时保留了标签（见后端 ChatService.sanitizeProductTags 只纠错不删除），
 * 渲染历史时需要把它们剥掉；实时流式消息由后端 flushSseBuffer 已剥离。
 * 覆盖：[PRODUCT:id] [ADD_TO_CART:id:qty:sku] [DELETE_FROM_CART:id] [CLEAR_CART] [DONE]
 */
const TAG_RE = /\[(?:PRODUCT|ADD_TO_CART|DELETE_FROM_CART):[^\]]*\]|\[(?:CLEAR_CART|DONE)\]/g;

export function stripChatTags(text: string): string {
  return text.replace(TAG_RE, '').replace(/[ \t]+\n/g, '\n').trim();
}
