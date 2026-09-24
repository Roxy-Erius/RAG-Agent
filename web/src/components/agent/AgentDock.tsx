import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { X, Send, ShoppingCart } from 'lucide-react';
import { Mascot } from './Mascot';
import { DesktopPet } from './DesktopPet';
import { sseChat } from '../../lib/sse';
import { getSessionId } from '../../lib/session';
import { productApi, cartApi } from '../../lib/api';
import { useCartStore } from '../../store/cart';
import { useAgentContext } from '../../store/agent';
import { ChatMessage, Product, SSEEvent } from '../../types';
import { ProductCard } from '../ProductCard';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';

export function AgentDock() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [unread, setUnread] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const agentCtx = useAgentContext();

  const addToCart = useCartStore((s) => s.addItem);
  const clearCart = useCartStore((s) => s.clear);
  const setItems = useCartStore((s) => s.setItems);

  // 会话 ID 持久化到 sessionStorage（关闭再开可恢复对话）
  const sessionId = getSessionId();

  // 自动滚动到底部
  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  // 页面切换时注入上下文
  useEffect(() => {
    if (pathname.startsWith('/product/')) {
      const id = pathname.split('/').pop();
      if (id) agentCtx.setContext({ productId: id });
    } else {
      agentCtx.clearContext();
    }
  }, [pathname]);

  const handleSend = useCallback(() => {
    const text = input.trim();
    if (!text || streaming) return;
    setInput('');
    setStreaming(true);
    if (!open) setOpen(true);
    setUnread(false);

    const userMsg: ChatMessage = { id: crypto.randomUUID(), role: 'user', text, timestamp: Date.now() };
    const assistantId = crypto.randomUUID();
    const assistantMsg: ChatMessage = { id: assistantId, role: 'assistant', text: '', cards: [], cartActions: [], streaming: true, timestamp: Date.now() };
    setMessages((prev) => [...prev, userMsg, assistantMsg]);

    let fullText = '';
    let cards: Product[] = [];
    let cartActions: ChatMessage['cartActions'] = [];

    const onEvent = async (evt: SSEEvent) => {
      if (evt.type === 'token') {
        fullText += evt.content || '';
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, text: fullText } : m)));
      } else if (evt.type === 'product' && evt.productId) {
        try {
          const p = await productApi.detail(evt.productId);
          cards.push(p);
          setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, cards: [...cards] } : m)));
        } catch (e) {
          console.warn('拉取商品详情失败', evt.productId, e);
        }
      } else if (evt.type === 'add_to_cart' && evt.productId) {
        // 对话驱动：mode='add' 追加 / mode='set' 设为 N 件 → **立即执行**（与 agent"已加入"文案一致）
        const action = { type: (evt.mode === 'set' ? 'set' : 'add') as 'add' | 'set', productId: evt.productId, label: evt.skuLabel, quantity: evt.quantity ?? 1 };
        const ok = await handleCartAction(action);
        cartActions.push({ ...action, done: ok });
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, cartActions: [...cartActions] } : m)));
      } else if (evt.type === 'delete_from_cart' && evt.cartItemId != null) {
        const ok = await handleCartAction({ type: 'delete', cartItemId: evt.cartItemId });
        cartActions.push({ type: 'delete', cartItemId: evt.cartItemId, done: ok });
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, cartActions: [...cartActions] } : m)));
      } else if (evt.type === 'clear_cart') {
        const ok = await handleCartAction({ type: 'clear' });
        cartActions.push({ type: 'clear', done: ok });
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, cartActions: [...cartActions] } : m)));
      } else if (evt.type === 'done') {
        setStreaming(false);
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, streaming: false } : m)));
        if (!open) setUnread(true);
      }
    };

    sseChat(
      { message: text, sessionId, conversationId: undefined, token: undefined },
      onEvent,
      (err) => {
        console.error('SSE error', err);
        setStreaming(false);
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantId
              ? { ...m, streaming: false, text: fullText || `⚠️ ${err.message}` }
              : m
          )
        );
      }
    );
  }, [input, streaming, open, sessionId]);

  const handleCartAction = async (action: { type: string; productId?: string; label?: string; quantity?: number; cartItemId?: number }): Promise<boolean> => {
    try {
      if ((action.type === 'add' || action.type === 'set') && action.productId) {
        const p = await productApi.detail(action.productId);
        const qty = action.quantity ?? 1;
        if (action.type === 'set') {
          // 设为 N 件：后端 set 是 upsert，但返回 Void → 重拉列表，用真实 id 覆盖本地
          await cartApi.set({ sessionId, productId: action.productId, quantity: qty });
          const fresh = await cartApi.list(sessionId);
          setItems(fresh.items);
        } else {
          const item = await cartApi.add({ sessionId, productId: action.productId, quantity: qty, skuLabel: action.label });
          addToCart({ ...item, productTitle: p.title, productPrice: p.basePrice, productImageBase64: p.imageBase64 });
        }
      } else if (action.type === 'delete' && action.cartItemId != null) {
        // 删单个：后端删除后重拉列表，保证本地 store 与后端一致（避免"本地没这条 → UI 不更新"）
        await cartApi.remove(action.cartItemId, sessionId);
        const fresh = await cartApi.list(sessionId);
        setItems(fresh.items);
      } else if (action.type === 'clear') {
        // 后端没有清整车的接口 → 先拉列表，逐个删（同时保证本地一致）
        const data = await cartApi.list(sessionId);
        for (const it of data.items) {
          await cartApi.remove(it.id, sessionId);
        }
        clearCart();
      }
      return true;
    } catch (e) {
      console.error('购物车操作失败', e);
      return false;
    }
  };

  return (
    <>
      {/* 悬浮按钮 */}
      {!open && (
        <DesktopPet
          streaming={streaming}
          unread={unread}
          onOpen={() => { setOpen(true); setUnread(false); }}
        />
      )}

      {/* 聊天面板 */}
      <div
        className={cn(
          'fixed bottom-0 right-0 z-50 w-full max-w-[400px] h-[70vh] sm:h-[600px] sm:bottom-6 sm:right-6 flex flex-col',
          'bg-[hsl(var(--surface))] border border-[hsl(var(--line))] shadow-lift sm:rounded-[var(--radius-lg)] overflow-hidden',
          'transition-all duration-300 ease-out',
          open ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4 pointer-events-none'
        )}
      >
        {/* header */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-[hsl(var(--line))] bg-gradient-to-b from-[hsl(var(--bg-soft))] to-[hsl(var(--surface))]">
          <span className="relative h-10 w-10 shrink-0 rounded-full bg-gradient-to-br from-[#FFF7EC] to-[#F2E2CB] border border-[hsl(var(--line))] flex items-center justify-center">
            <span className={cn('block', streaming ? 'mascot-wiggle' : 'mascot-bob')}>
              <Mascot size={32} mood={streaming ? 'thinking' : 'idle'} />
            </span>
            <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full bg-[hsl(var(--ok))] border-2 border-[hsl(var(--surface))]" />
          </span>
          <div className="flex-1 min-w-0">
            <div className="font-semibold text-sm text-[hsl(var(--ink))] flex items-center gap-1.5">
              小买
              <span className="text-[10px] font-normal chip-accent px-1.5 py-0">AI 买手</span>
            </div>
            <div className="text-xs text-[hsl(var(--ink-faint))] truncate">
              {agentCtx.productId ? `正在看：${agentCtx.productTitle || agentCtx.productId}` : '在线 · 随时为你挑选好物'}
            </div>
          </div>
          <Button variant="ghost" size="icon" onClick={() => setOpen(false)}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* messages */}
        <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
          {messages.length === 0 && (
            <div className="text-center py-8 space-y-4">
              <div className="mx-auto w-fit mascot-bob">
                <Mascot size={84} mood="happy" />
              </div>
              <div className="space-y-1">
                <p className="text-[15px] font-medium text-[hsl(var(--ink))]">你好呀，我是小买 👋</p>
                <p className="text-xs text-[hsl(var(--ink-faint))]">你的专属 AI 买手，帮你挑到合适的</p>
              </div>
              <div className="flex flex-wrap gap-2 justify-center pt-1">
                {['推荐一款保湿面霜', '油皮适合什么精华', '帮我找双跑步鞋', '有什么咖啡推荐'].map((s) => (
                  <button
                    key={s}
                    onClick={() => setInput(s)}
                    className="text-xs px-3 py-1.5 rounded-full bg-[hsl(var(--surface-2))] text-[hsl(var(--ink-soft))] hover:bg-[hsl(var(--accent-soft))] hover:text-[hsl(var(--accent-strong))] transition-colors"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}
          {messages.map((m) => (
            <div key={m.id} className={cn('flex gap-2.5', m.role === 'user' ? 'justify-end' : 'justify-start')}>
              {m.role === 'assistant' && (
                <span className="h-7 w-7 shrink-0 rounded-full bg-gradient-to-br from-[#FFF7EC] to-[#F2E2CB] border border-[hsl(var(--line))] flex items-center justify-center mt-0.5 overflow-hidden">
                  <Mascot size={24} mood={m.streaming ? 'thinking' : 'idle'} blink={false} />
                </span>
              )}
              <div className={cn('max-w-[82%] space-y-2', m.role === 'user' && 'text-right')}>
                {/* 气泡 */}
                <div
                  className={cn(
                    'inline-block px-3.5 py-2.5 text-sm leading-relaxed rounded-[var(--radius-lg)]',
                    m.role === 'user'
                      ? 'bg-[hsl(var(--accent))] text-[#fff8ef] rounded-br-[var(--radius-sm)]'
                      : 'bg-[hsl(var(--surface))] border border-[hsl(var(--line))] shadow-soft text-[hsl(var(--ink))] rounded-bl-[var(--radius-sm)]'
                  )}
                >
                  {m.streaming && !m.text && (
                    <span className="flex gap-1.5 py-1">
                      <span className="h-1.5 w-1.5 rounded-full bg-[hsl(var(--ink-faint))] typing-dot" />
                      <span className="h-1.5 w-1.5 rounded-full bg-[hsl(var(--ink-faint))] typing-dot" />
                      <span className="h-1.5 w-1.5 rounded-full bg-[hsl(var(--ink-faint))] typing-dot" />
                    </span>
                  )}
                  <span className="whitespace-pre-wrap">{m.text}</span>
                  {m.streaming && m.text && <span className="inline-block w-0.5 h-4 bg-[hsl(var(--accent))] animate-pulse ml-0.5 align-middle" />}
                </div>

                {/* 商品卡片 */}
                {m.cards && m.cards.length > 0 && (
                  <div className="space-y-2 anim-slide-up">
                    {m.cards.map((p) => (
                      <ProductCard key={p.productId} product={p} compact onClick={() => navigate(`/product/${p.productId}`)} />
                    ))}
                  </div>
                )}

                {/* 购物车动作 */}
                {m.cartActions && m.cartActions.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {m.cartActions.map((a, i) =>
                      a.done ? (
                        <span key={i} className="inline-flex items-center gap-1 text-xs text-[hsl(var(--ok))] bg-[hsl(var(--ok))]/10 px-2.5 py-1 rounded-full">
                          <ShoppingCart className="h-3.5 w-3.5" />
                          {a.type === 'add' && '已加入购物车'}
                          {a.type === 'set' && `已设为 ${a.quantity ?? 1} 件`}
                          {a.type === 'delete' && '已移除'}
                          {a.type === 'clear' && '已清空购物车'}
                          {a.label && <span className="opacity-80">({a.label})</span>}
                        </span>
                      ) : (
                        <Button key={i} size="sm" variant={a.type === 'clear' || a.type === 'delete' ? 'outline' : 'ok'} onClick={() => handleCartAction(a)}>
                          <ShoppingCart className="h-3.5 w-3.5" />
                          {a.type === 'add' && '加入购物车'}
                          {a.type === 'set' && `设为 ${a.quantity ?? 1} 件`}
                          {a.type === 'delete' && '移除'}
                          {a.type === 'clear' && '清空购物车'}
                          {a.label && <span className="text-xs opacity-80">({a.label})</span>}
                        </Button>
                      )
                    )}
                  </div>
                )}
              </div>
              {m.role === 'user' && (
                <span className="h-7 w-7 shrink-0 rounded-full bg-[hsl(var(--surface-2))] flex items-center justify-center text-xs font-medium text-[hsl(var(--ink))] mt-0.5">我</span>
              )}
            </div>
          ))}
        </div>

        {/* input */}
        <div className="border-t border-[hsl(var(--line))] px-3 py-2.5 bg-[hsl(var(--surface))]">
          <form
            className="flex items-center gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="说点什么，比如：帮我找一款油皮面霜…"
              className="flex-1 h-10 px-4 rounded-full border border-[hsl(var(--line))] bg-[hsl(var(--bg-soft))] text-sm placeholder:text-[hsl(var(--ink-faint))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--accent))] focus:ring-offset-2 transition-all"
            />
            <Button
              type="submit"
              size="icon"
              disabled={streaming || !input.trim()}
              className="h-10 w-10 rounded-full shrink-0"
            >
              <Send className="h-4 w-4" />
            </Button>
          </form>
        </div>
      </div>
    </>
  );
}
