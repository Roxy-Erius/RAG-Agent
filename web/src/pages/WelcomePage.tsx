import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles, LogIn, UserPlus, Footprints, ChevronRight, MessageCircle, ShieldCheck, Package } from 'lucide-react';
import { useAuthStore } from '../store/auth';

export function WelcomePage() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);

  // 已登录用户访问入口页 → 直接回货架
  useEffect(() => {
    if (token) navigate('/home', { replace: true });
  }, [token, navigate]);

  const entries = [
    {
      key: 'login',
      icon: LogIn,
      title: '登录',
      desc: '找回购物车与订单，跨端同步',
      action: () => navigate('/login'),
    },
    {
      key: 'register',
      icon: UserPlus,
      title: '注册',
      desc: '开通专属买手账号，享会员权益',
      action: () => navigate('/login', { state: { mode: 'register' } }),
    },
    {
      key: 'guest',
      icon: Footprints,
      title: '游客进入',
      desc: '免登录逛逛，稍后可随时登录',
      action: () => navigate('/home'),
    },
  ];

  return (
    <div className="min-h-screen flex flex-col">
      {/* 顶部渐变大背景 */}
      <div className="flex-1 flex items-center justify-center px-4 pb-8">
        <div className="w-full max-w-md text-center space-y-8">
          {/* Logo */}
          <div className="space-y-3 anim-slide-up">
            <span className="mx-auto h-16 w-16 rounded-3xl bg-gradient-to-br from-[hsl(var(--accent))] to-[hsl(var(--accent-strong))] flex items-center justify-center text-[#fff8ef] shadow-[0_8px_30px_-8px_rgba(181,120,63,0.6)]">
              <Sparkles className="h-8 w-8" />
            </span>
            <h1 className="font-display text-3xl font-bold text-[hsl(var(--ink))] tracking-wide">
              精选导购
            </h1>
            <p className="text-sm text-[hsl(var(--ink-soft))] leading-relaxed max-w-xs mx-auto">
              一个会聊天的买手店——每件好物都由你的专属导购助手相伴挑选
            </p>
          </div>

          {/* 三个入口 */}
          <div className="space-y-3 anim-fade-in" style={{ animationDelay: '0.1s' }}>
            {entries.map((e, i) => (
              <button
                key={e.key}
                onClick={e.action}
                className="card card-hover w-full flex items-center gap-3.5 p-4 text-left group"
                style={{ animationDelay: `${0.15 + i * 0.08}s` }}
              >
                <span className="h-11 w-11 shrink-0 rounded-[var(--radius-sm)] bg-[hsl(var(--accent-soft))] flex items-center justify-center text-[hsl(var(--accent-strong))] group-hover:bg-[hsl(var(--accent))] group-hover:text-[#fff8ef] transition-colors">
                  <e.icon className="h-5 w-5" />
                </span>
                <span className="flex-1">
                  <span className="block font-medium text-[hsl(var(--ink))]">{e.title}</span>
                  <span className="block text-xs text-[hsl(var(--ink-faint))] mt-0.5">{e.desc}</span>
                </span>
                <ChevronRight className="h-5 w-5 text-[hsl(var(--ink-faint))] group-hover:text-[hsl(var(--accent))] transition-colors" />
              </button>
            ))}
          </div>

          {/* 底部特性 */}
          <div className="pt-2 flex items-center justify-center gap-6 text-[hsl(var(--ink-faint))]">
            <span className="flex items-center gap-1.5 text-xs">
              <MessageCircle className="h-3.5 w-3.5" /> 智能导购
            </span>
            <span className="flex items-center gap-1.5 text-xs">
              <ShieldCheck className="h-3.5 w-3.5" /> 安心退换
            </span>
            <span className="flex items-center gap-1.5 text-xs">
              <Package className="h-3.5 w-3.5" /> 精选好物
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
