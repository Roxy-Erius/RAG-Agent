import { useId } from 'react';

interface MascotProps {
  size?: number;
  className?: string;
  mood?: 'idle' | 'happy' | 'thinking';
  /** 是否播放眨眼动画 */
  blink?: boolean;
}

/**
 * 导购助手吉祥物「小买」——暖色小狐狸。
 * 纯内联 SVG，无外部资源，可任意缩放。
 */
export function Mascot({ size = 40, className, mood = 'idle', blink = true }: MascotProps) {
  const uid = useId().replace(/:/g, '');
  const furId = `fur-${uid}`;
  const creamId = `cream-${uid}`;

  return (
    <svg
      viewBox="0 0 100 100"
      width={size}
      height={size}
      className={className}
      role="img"
      aria-label="导购助手小买"
    >
      <defs>
        <linearGradient id={furId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#E09A5C" />
          <stop offset="100%" stopColor="#C47A4A" />
        </linearGradient>
        <linearGradient id={creamId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#FFFBF4" />
          <stop offset="100%" stopColor="#F7E7D2" />
        </linearGradient>
      </defs>

      {/* 耳朵（用同色描边把三角倒角，做出圆润感） */}
      <path d="M25 33 L24 9 L48 27 Z" fill={`url(#${furId})`} stroke={`url(#${furId})`} strokeWidth="8" strokeLinejoin="round" />
      <path d="M75 33 L76 9 L52 27 Z" fill={`url(#${furId})`} stroke={`url(#${furId})`} strokeWidth="8" strokeLinejoin="round" />
      <path d="M30 29 L29 15 L42 27 Z" fill="#8B5A2B" opacity="0.45" />
      <path d="M70 29 L71 15 L58 27 Z" fill="#8B5A2B" opacity="0.45" />

      {/* 头 */}
      <ellipse cx="50" cy="57" rx="35" ry="31" fill={`url(#${furId})`} />

      {/* 腮/口鼻奶油区 */}
      <ellipse cx="27" cy="64" rx="9" ry="10" fill={`url(#${creamId})`} opacity="0.95" />
      <ellipse cx="73" cy="64" rx="9" ry="10" fill={`url(#${creamId})`} opacity="0.95" />
      <ellipse cx="50" cy="68" rx="21" ry="16" fill={`url(#${creamId})`} />

      {/* 腮红 */}
      <ellipse cx="30" cy="67" rx="6" ry="4" fill="#E8A87C" opacity="0.6" />
      <ellipse cx="70" cy="67" rx="6" ry="4" fill="#E8A87C" opacity="0.6" />

      {/* 眼睛 */}
      {mood === 'happy' ? (
        <g stroke="#3D3226" strokeWidth="3" fill="none" strokeLinecap="round">
          <path d="M34 56 Q39 50 44 56" />
          <path d="M56 56 Q61 50 66 56" />
        </g>
      ) : (
        <g className={blink ? 'mascot-eyes' : undefined}>
          <ellipse cx="39" cy="55" rx="4.8" ry="5.8" fill="#3D3226" />
          <ellipse cx="61" cy="55" rx="4.8" ry="5.8" fill="#3D3226" />
          <circle cx="40.7" cy="53.1" r="1.7" fill="#FFFFFF" />
          <circle cx="62.7" cy="53.1" r="1.7" fill="#FFFFFF" />
        </g>
      )}

      {/* 鼻子 */}
      <path d="M50 61 Q54.5 61 54.5 64 Q54.5 68 50 69.2 Q45.5 68 45.5 64 Q45.5 61 50 61 Z" fill="#3D3226" />

      {/* 微笑 */}
      <path
        d="M50 69.5 Q50 74.5 45 74.5 M50 69.5 Q50 74.5 55 74.5"
        stroke="#3D3226"
        strokeWidth="1.7"
        fill="none"
        strokeLinecap="round"
      />
    </svg>
  );
}
