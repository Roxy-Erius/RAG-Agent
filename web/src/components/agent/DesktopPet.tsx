import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { useCartStore } from '../../store/cart';
import { useAuthStore } from '../../store/auth';

type PetState = 'idle' | 'walk' | 'sit' | 'sleep' | 'happy' | 'thinking' | 'drag';

const PET_W = 104;       // 桌宠显示宽度(px)
const PET_H = 80;        // 桌宠近似显示高度(px)
const PAD = 10;          // 边界内边距
const SLEEP_AFTER = 30000;   // 无互动多久睡觉(ms)

interface DesktopPetProps {
  onOpen: () => void;
  streaming?: boolean;
  unread?: boolean;
}

/** 睡觉姿势：蜷成一团，大尾巴环到身前，头枕着尾巴 */
function PetSleeping() {
  return (
    <svg viewBox="0 0 160 114" className="w-full h-auto block anim-fade-in">
      <defs>
        <linearGradient id="petFurSleep" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#E4A063" />
          <stop offset="100%" stopColor="#C47A4A" />
        </linearGradient>
      </defs>
      <g className="pet-sleep-breathe">
        {/* 蜷起的身团 */}
        <ellipse cx="76" cy="74" rx="44" ry="28" fill="url(#petFurSleep)" />
        <ellipse cx="78" cy="84" rx="26" ry="12" fill="#FFFBF4" opacity="0.9" />

        {/* 大尾巴：从背后绕一圈到身前 */}
        <path
          d="M52 58 C22 58 12 90 40 100 C68 110 100 102 116 88"
          stroke="#D98A4F"
          strokeWidth="21"
          fill="none"
          strokeLinecap="round"
        />
        {/* 尾巴蓬松白尖（贴着鼻子） */}
        <circle cx="116" cy="88" r="12" fill="#FFFBF4" />
        <circle cx="120" cy="84" r="6" fill="#F0DFC9" opacity="0.7" />

        {/* 头：低垂枕着尾巴 */}
        <path d="M104 64 L110 46 L122 60 Z" fill="#D98A4F" stroke="#D98A4F" strokeWidth="6" strokeLinejoin="round" />
        <circle cx="117" cy="80" r="23" fill="url(#petFurSleep)" />
        <ellipse cx="132" cy="86" rx="12" ry="9" fill="#FFFBF4" />
        {/* 闭眼（两条上弯弧线 = 睡得很香） */}
        <path d="M109 78 Q115 83 121 78" stroke="#3D3226" strokeWidth="2.6" fill="none" strokeLinecap="round" />
        {/* 鼻子 */}
        <path d="M140 82 Q146 82 146 86 Q146 90 140 90.5 Q134 90 134 86 Q134 82 140 82 Z" fill="#3D3226" />
        {/* 腮红 */}
        <ellipse cx="122" cy="88" rx="5" ry="3.4" fill="#E8A87C" opacity="0.6" />
      </g>
    </svg>
  );
}

/** 侧面小狐狸（面向右），身体拆成部件以驱动动画 */
function PetFox({ state, facing }: { state: PetState; facing: number }) {
  if (state === 'sleep') return <PetSleeping />;
  const sitting = state === 'sit';
  return (
    <svg
      viewBox="0 0 160 114"
      className="w-full h-auto block"
      style={{ transform: `scaleX(${facing})`, transformOrigin: 'center' }}
    >
      <defs>
        <linearGradient id="petFur" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#E4A063" />
          <stop offset="100%" stopColor="#C47A4A" />
        </linearGradient>
      </defs>

      {/* 尾巴 */}
      <g className="pet-tail">
        <path d="M50 58 C16 60 6 26 30 14 C40 30 46 44 60 54 Z" fill="#D98A4F" />
        <circle cx="28" cy="20" r="8" fill="#FFFBF4" opacity="0.85" />
      </g>

      {/* 后腿 */}
      <g className={cn(sitting && 'pet-legs-tucked')}>
        <rect className="pet-leg pet-leg-a" x="46" y="80" width="11" height="22" rx="5.5" fill="#B0673B" />
        <rect className="pet-leg pet-leg-b" x="62" y="82" width="11" height="20" rx="5.5" fill="#B0673B" />
      </g>

      {/* 身体 */}
      <g className="pet-breathe">
        <ellipse cx="78" cy="64" rx="38" ry="22" fill="url(#petFur)" />
        <ellipse cx="84" cy="72" rx="24" ry="11" fill="#FFFBF4" opacity="0.95" />
      </g>

      {/* 前腿 */}
      <g className={cn(sitting && 'pet-legs-tucked')}>
        <rect className="pet-leg pet-leg-b" x="90" y="82" width="11" height="20" rx="5.5" fill="#C47A4A" />
        <rect className="pet-leg pet-leg-a" x="104" y="80" width="11" height="22" rx="5.5" fill="#C47A4A" />
      </g>

      {/* 头 */}
      <g className={cn(sitting && 'pet-head-sit')}>
        <path d="M104 26 L112 2 L130 22 Z" fill="#D98A4F" stroke="#D98A4F" strokeWidth="7" strokeLinejoin="round" />
        <path d="M119 21 L123 8 L130 19 Z" fill="#8B5A2B" opacity="0.4" />
        <circle cx="121" cy="44" r="26" fill="url(#petFur)" />
        <ellipse cx="140" cy="51" rx="14" ry="11" fill="#FFFBF4" />
        <path d="M150 46 Q156 46 156 50 Q156 54 150 55 Q144 54 144 50 Q144 46 150 46 Z" fill="#3D3226" />
        <g className="pet-eye">
          <ellipse cx="125" cy="42" rx="4" ry="4.8" fill="#3D3226" />
          <circle cx="126.4" cy="40.4" r="1.4" fill="#fff" />
        </g>
        <ellipse cx="129" cy="52" rx="5" ry="3.4" fill="#E8A87C" opacity="0.6" />
      </g>
    </svg>
  );
}

export function DesktopPet({ onOpen, streaming = false, unread = false }: DesktopPetProps) {
  const minX = PAD;
  const maxX = () => Math.max(minX, window.innerWidth - PET_W - PAD);
  const minY = PAD;
  const maxY = () => Math.max(minY, window.innerHeight - PET_H - PAD);

  const [x, setX] = useState(() => Math.max(minX, window.innerWidth - PET_W - 28));
  const [y, setY] = useState(() => minY);   // y = 距底部距离
  const [state, setState] = useState<PetState>('idle');
  const [facing, setFacing] = useState(-1);
  const [moveMs, setMoveMs] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [bubble, setBubble] = useState<string | null>(null);

  const posRef = useRef({ x, y });
  const stateRef = useRef(state);
  const lastInteract = useRef(Date.now());
  const dragInfo = useRef<{ startX: number; startY: number; moved: boolean } | null>(null);
  const moveTimer = useRef<number | undefined>(undefined);
  const happyTimer = useRef<number | undefined>(undefined);
  const tipIndex = useRef(0);

  posRef.current = { x, y };
  stateRef.current = state;

  // ===== 气泡文案（结合登录 / 购物车状态，引导用户）=====
  const token = useAuthStore((s) => s.token);
  const cartCount = useCartStore((s) => s.items.reduce((n, i) => n + i.quantity, 0));
  const tips = useMemo(() => {
    const list = [
      '试试问我：推荐一款保湿面霜～',
      '告诉我预算，我帮你挑最合适的',
      '点商品卡片就能看详情哦',
      '我能帮你挑礼物、找平替、比价',
      '今天想买点什么？',
      '不知道选啥？说说你的需求就行',
    ];
    if (!token) list.splice(2, 0, '登录后购物车和订单会云端同步');
    if (cartCount > 0) list.splice(1, 0, `购物车里有 ${cartCount} 件啦，要结算吗？`);
    return list;
  }, [token, cartCount]);

  const touch = () => { lastInteract.current = Date.now(); };
  const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

  const walkTo = useCallback((tx: number, ty: number) => {
    if (dragging) return;
    const cxp = posRef.current.x;
    const cyp = posRef.current.y;
    const nx = clamp(tx, minX, maxX());
    const ny = clamp(ty, minY, maxY());
    const dist = Math.hypot(nx - cxp, ny - cyp);
    if (dist < 24) return;
    setFacing(nx > cxp ? 1 : -1);
    const dur = clamp(dist / 0.08, 800, 5600);
    setMoveMs(dur);
    setState('walk');
    setX(nx);
    setY(ny);
    window.clearTimeout(moveTimer.current);
    moveTimer.current = window.setTimeout(() => {
      if (stateRef.current === 'walk') setState('idle');
    }, dur + 60);
  }, [dragging]);

  // ===== 自主行为循环 =====
  useEffect(() => {
    const loop = window.setInterval(() => {
      if (dragging) return;
      const idleFor = Date.now() - lastInteract.current;
      if (idleFor > SLEEP_AFTER) { setState('sleep'); return; }
      if (streaming) { setState('thinking'); return; }

      const r = Math.random();
      if (stateRef.current === 'sleep') { if (r < 0.6) setState('idle'); return; }
      if (r < 0.55) {
        walkTo(randX(), randY());                       // 页面里自由走动
      } else if (r < 0.72) {
        setFacing(Math.random() < 0.5 ? 1 : -1);
        setState('sit');
        window.setTimeout(() => { if (stateRef.current === 'sit') setState('idle'); }, 2600);
      } else {
        setFacing(Math.random() < 0.5 ? 1 : -1);
        setState('idle');
      }
    }, 3600);
    return () => window.clearInterval(loop);
  }, [dragging, streaming, walkTo, tips]);

  function randX() { return minX + Math.random() * (maxX() - minX); }
  function randY() {
    // 偏向中下区域活动，但全页可达
    const lo = minY;
    const hi = maxY();
    return lo + Math.random() * (hi - lo) * 0.95;
  }

  // ===== 说话引导：醒着时定时冒泡 =====
  useEffect(() => {
    let hideTimer: number | undefined;
    const show = () => {
      if (dragging || streaming || stateRef.current === 'sleep') return;
      if (Date.now() - lastInteract.current > SLEEP_AFTER - 3000) return;
      const msg = tips[tipIndex.current % tips.length];
      tipIndex.current += 1;
      setBubble(msg);
      window.clearTimeout(hideTimer);
      hideTimer = window.setTimeout(() => setBubble(null), 5200);
    };
    const first = window.setTimeout(show, 3500);
    const loop = window.setInterval(show, 13000);
    return () => { window.clearTimeout(first); window.clearInterval(loop); window.clearTimeout(hideTimer); };
  }, [dragging, streaming, tips]);

  // 睡觉 / 打开对话时不说话
  useEffect(() => { if (state === 'sleep') setBubble(null); }, [state]);

  // streaming → 思考脸
  useEffect(() => {
    if (streaming) setState('thinking');
    else if (stateRef.current === 'thinking') setState('idle');
  }, [streaming]);

  // 窗口尺寸变化时收回边界内
  useEffect(() => {
    const onResize = () => {
      setX((v) => clamp(v, minX, maxX()));
      setY((v) => clamp(v, minY, maxY()));
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  // ===== 拖拽 / 点击 =====
  const onPointerDown = (e: React.PointerEvent) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    dragInfo.current = { startX: e.clientX, startY: e.clientY, moved: false };
    setDragging(true);
    setState('drag');
    setBubble(null);
    touch();
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const info = dragInfo.current;
    if (!info || !dragging) return;
    if (Math.abs(e.clientX - info.startX) > 4 || Math.abs(e.clientY - info.startY) > 4) info.moved = true;
    setX(clamp(e.clientX - PET_W / 2, 4, window.innerWidth - PET_W - 4));
    setY(clamp(window.innerHeight - e.clientY - PET_H / 2, 4, window.innerHeight - PET_H - 4));
  };

  const onPointerUp = () => {
    const info = dragInfo.current;
    dragInfo.current = null;
    setDragging(false);
    touch();
    if (info && !info.moved) {
      onOpen();                     // 点击（未拖动）→ 打开对话
      setState('idle');
    } else {
      setState('happy');            // 松手后停在原地，开心一下
      window.clearTimeout(happyTimer.current);
      happyTimer.current = window.setTimeout(() => setState('idle'), 520);
    }
  };

  const transition = dragging
    ? 'none'
    : state === 'walk'
      ? `left ${moveMs}ms linear, bottom ${moveMs}ms linear`
      : 'left .35s ease, bottom .35s ease';

  const bubbleRight = x + PET_W / 2 > window.innerWidth - 140;

  return (
    <div
      className="fixed z-40 select-none touch-none cursor-grab active:cursor-grabbing"
      style={{ left: x, bottom: y, width: PET_W, transition, willChange: 'left, bottom' }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerEnter={touch}
      role="button"
      aria-label="打开导购助手小买"
      title="点我聊两句 · 也可以拖着我走"
    >
      {/* 说话气泡 */}
      {bubble && state !== 'sleep' && (
        <div className={cn('absolute bottom-full mb-2 w-max max-w-[210px] anim-slide-up', bubbleRight ? 'right-0' : 'left-0')}>
          <div className="relative rounded-2xl bg-[hsl(var(--surface))] border border-[hsl(var(--line))] shadow-lift px-3.5 py-2.5 text-xs leading-relaxed text-[hsl(var(--ink))]">
            {bubble}
            <button
              onPointerDown={(e) => e.stopPropagation()}
              onClick={(e) => { e.stopPropagation(); setBubble(null); }}
              className="absolute -top-1.5 -right-1.5 h-4 w-4 rounded-full bg-[hsl(var(--surface-2))] border border-[hsl(var(--line))] flex items-center justify-center text-[hsl(var(--ink-faint))] hover:text-[hsl(var(--ink))]"
              aria-label="收起"
            >
              <X className="h-2.5 w-2.5" />
            </button>
            <span
              className={cn(
                'absolute -bottom-[6px] h-3 w-3 rotate-45 bg-[hsl(var(--surface))] border-b border-r border-[hsl(var(--line))]',
                bubbleRight ? 'right-6' : 'left-6'
              )}
            />
          </div>
        </div>
      )}

      {/* 思考气泡 */}
      {state === 'thinking' && (
        <div className="absolute -top-3 left-6 flex items-center gap-1 rounded-full bg-white border border-[hsl(var(--line))] shadow-soft px-2.5 py-1.5 anim-scale-in">
          <span className="h-1.5 w-1.5 rounded-full bg-[hsl(var(--accent))] typing-dot" />
          <span className="h-1.5 w-1.5 rounded-full bg-[hsl(var(--accent))] typing-dot" />
          <span className="h-1.5 w-1.5 rounded-full bg-[hsl(var(--accent))] typing-dot" />
        </div>
      )}
      {/* 睡觉 Zzz */}
      {state === 'sleep' && (
        <div className="absolute -top-2 left-8 text-[hsl(var(--ink-faint))] font-display font-bold">
          <span className="pet-zzz inline-block text-sm">Z</span>
          <span className="pet-zzz inline-block text-xs" style={{ animationDelay: '.5s' }}>z</span>
          <span className="pet-zzz inline-block text-[10px]" style={{ animationDelay: '1s' }}>z</span>
        </div>
      )}

      <div
        className={cn(
          state === 'walk' && 'pet-walk',
          state === 'happy' && 'pet-jump',
          state === 'thinking' && 'pet-think',
          state === 'idle' && 'pet-idle'
        )}
      >
        <PetFox state={state} facing={facing} />
      </div>

      {unread && (
        <span className="absolute top-1 right-3 h-3.5 w-3.5 rounded-full bg-[hsl(var(--danger))] border-2 border-[hsl(var(--surface))] animate-pulse" />
      )}
      {/* 脚下阴影（拖高时变淡） */}
      <span
        className="pointer-events-none absolute left-1/2 -translate-x-1/2 -bottom-1 h-2 w-12 rounded-full bg-[hsl(var(--ink))]/10 blur-[2px]"
        style={{ opacity: Math.max(0, 1 - y / 260) }}
      />
    </div>
  );
}
