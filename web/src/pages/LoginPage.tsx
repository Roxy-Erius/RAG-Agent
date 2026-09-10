import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Sparkles, User, Lock, Loader2 } from 'lucide-react';
import { useAuthStore } from '../store/auth';
import { Button } from '../components/ui/button';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, register, loading } = useAuthStore();

  // 允许从入口指定初始模式（如欢迎页点「注册」直接进入注册）
  const initialMode = (location.state as { mode?: 'login' | 'register' })?.mode ?? 'login';
  const [mode, setMode] = useState<'login' | 'register'>(initialMode);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const from = (location.state as { from?: string })?.from || '/';

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      if (mode === 'login') {
        await login(username, password);
      } else {
        await register(username, password);
        // 注册成功后自动切到登录
        setMode('login');
        setError('注册成功，请登录');
        return;
      }
      navigate(from, { replace: true });
    } catch (e) {
      setError(e instanceof Error ? e.message : '操作失败');
    }
  };

  return (
    <div className="min-h-[80vh] flex items-center justify-center">
      <div className="w-full max-w-sm">
        <div className="card p-6 space-y-5">
          {/* 标题 */}
          <div className="text-center space-y-2">
            <span className="h-12 w-12 mx-auto rounded-2xl bg-gradient-to-br from-[hsl(var(--accent))] to-[hsl(var(--accent-strong))] flex items-center justify-center text-[#fff8ef] shadow-[0_4px_16px_-4px_rgba(181,120,63,0.5)]">
              <Sparkles className="h-6 w-6" />
            </span>
            <h1 className="font-display text-xl font-bold text-[hsl(var(--ink))]">
              {mode === 'login' ? '欢迎回来' : '创建账号'}
            </h1>
            <p className="text-sm text-[hsl(var(--ink-soft))]">
              {mode === 'login' ? '登录后购物车、订单更持久' : '注册即享专属买手服务'}
            </p>
          </div>

          {/* 表单 */}
          <form onSubmit={submit} className="space-y-3">
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[hsl(var(--ink-faint))]" />
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="用户名"
                className="w-full h-11 pl-10 pr-3 rounded-[var(--radius-sm)] border border-[hsl(var(--line))] bg-[hsl(var(--surface))] text-sm placeholder:text-[hsl(var(--ink-faint))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--accent))] focus:ring-offset-2 transition-all"
              />
            </div>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[hsl(var(--ink-faint))]" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === 'login' ? '密码' : '密码（至少 6 位）'}
                className="w-full h-11 pl-10 pr-3 rounded-[var(--radius-sm)] border border-[hsl(var(--line))] bg-[hsl(var(--surface))] text-sm placeholder:text-[hsl(var(--ink-faint))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--accent))] focus:ring-offset-2 transition-all"
              />
            </div>

            {error && (
              <p className="text-sm text-[hsl(var(--danger))] text-center">{error}</p>
            )}

            <Button type="submit" size="lg" className="w-full" disabled={loading || !username || !password}>
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              {mode === 'login' ? '登录' : '注册'}
            </Button>
          </form>

          {/* 切换 */}
          <div className="text-center text-sm text-[hsl(var(--ink-soft))]">
            {mode === 'login' ? (
              <>还没有账号？<button className="text-[hsl(var(--accent))] hover:underline" onClick={() => { setMode('register'); setError(''); }}>去注册</button></>
            ) : (
              <>已有账号？<button className="text-[hsl(var(--accent))] hover:underline" onClick={() => { setMode('login'); setError(''); }}>返回登录</button></>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
