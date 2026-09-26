import { useEffect, useState, type MouseEvent } from 'react';
import { Plus, Search, Trash2, MessageSquare, X } from 'lucide-react';
import { conversationApi } from '../../lib/api';
import { Conversation } from '../../types';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { cn } from '../../lib/utils';

interface Props {
  currentId: string | null;
  onSelect: (conversationId: string) => void;
  onNew: () => void;
  onClose: () => void;
}

export function ConversationHistory({ currentId, onSelect, onNew, onClose }: Props) {
  const [list, setList] = useState<Conversation[]>([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(false);

  // 关键词搜索（防抖）
  useEffect(() => {
    let cancelled = false;
    const t = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await conversationApi.list(q);
        if (!cancelled) setList(data);
      } catch (e) {
        console.warn('加载会话列表失败', e);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [q]);

  const handleDelete = async (e: MouseEvent, cid: string) => {
    e.stopPropagation();
    try {
      await conversationApi.remove(cid);
      setList((prev) => prev.filter((c) => c.conversationId !== cid));
      if (cid === currentId) onNew();
    } catch (err) {
      console.warn('删除会话失败', err);
    }
  };

  return (
    <div className="absolute inset-0 z-10 flex flex-col bg-[hsl(var(--surface))]">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-[hsl(var(--line))]">
        <span className="flex-1 text-sm font-medium text-[hsl(var(--ink))]">历史对话</span>
        <Button size="sm" variant="ok" onClick={onNew}>
          <Plus className="h-3.5 w-3.5" /> 新对话
        </Button>
        <Button size="icon" variant="ghost" onClick={onClose} title="返回">
          <X className="h-4 w-4" />
        </Button>
      </div>

      <div className="px-4 py-2.5 border-b border-[hsl(var(--line))]">
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[hsl(var(--ink-faint))]" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="搜索历史对话…"
            className="h-9 pl-8"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-2 py-2">
        {loading && list.length === 0 && (
          <p className="text-xs text-[hsl(var(--ink-faint))] text-center py-3">加载中…</p>
        )}
        {!loading && list.length === 0 && (
          <p className="text-xs text-[hsl(var(--ink-faint))] text-center py-8">暂无历史对话</p>
        )}
        {list.map((c) => (
          <div
            key={c.conversationId}
            onClick={() => onSelect(c.conversationId)}
            className={cn(
              'group flex items-center gap-2 px-3 py-2.5 rounded-[var(--radius-sm)] cursor-pointer hover:bg-[hsl(var(--bg-soft))]',
              c.conversationId === currentId && 'bg-[hsl(var(--accent-soft))]'
            )}
          >
            <MessageSquare className="h-4 w-4 shrink-0 text-[hsl(var(--ink-faint))]" />
            <div className="flex-1 min-w-0">
              <div className="text-sm text-[hsl(var(--ink))] truncate">{c.title || '新对话'}</div>
              <div className="text-[11px] text-[hsl(var(--ink-faint))] truncate">{formatTime(c.updatedAt)}</div>
            </div>
            <button
              onClick={(e) => handleDelete(e, c.conversationId)}
              className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-[hsl(var(--surface-2))] text-[hsl(var(--ink-faint))]"
              title="删除"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function formatTime(s?: string): string {
  if (!s) return '';
  const d = new Date(s);
  if (isNaN(d.getTime())) return String(s);
  const diff = Date.now() - d.getTime();
  if (diff < 60_000) return '刚刚';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`;
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}
