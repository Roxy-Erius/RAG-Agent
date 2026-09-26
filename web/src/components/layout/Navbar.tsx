import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ShoppingCart, Sparkles, Search, User } from 'lucide-react';
import { useCartStore } from '../../store/cart';
import { useAuthStore } from '../../store/auth';
import { cn } from '../../lib/utils';
import { useState } from 'react';

export function Navbar() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const count = useCartStore((s) => s.count());
  const { token, username, logout } = useAuthStore();
  const [q, setQ] = useState('');

  const nav = [
    { to: '/home', label: '货架' },
    { to: '/orders', label: '订单' },
  ];

  return (
    <header className="sticky top-0 z-40 bg-[hsl(var(--bg))]/80 backdrop-blur-md border-b border-[hsl(var(--line))]">
      <div className="max-w-6xl mx-auto px-4 h-14 flex items-center gap-4">
        {/* Logo */}
        <Link to="/home" className="flex items-center gap-2 shrink-0">
          <span className="h-7 w-7 rounded-full bg-gradient-to-br from-[hsl(var(--accent))] to-[hsl(var(--accent-strong))] flex items-center justify-center text-[#fff8ef]">
            <Sparkles className="h-4 w-4" />
          </span>
          <span className="font-display font-bold text-lg tracking-wide text-[hsl(var(--ink))]">
            精选导购
          </span>
        </Link>

        {/* Nav links */}
        <nav className="hidden sm:flex items-center gap-1">
          {nav.map((n) => (
            <Link
              key={n.to}
              to={n.to}
              className={cn(
                'px-3 py-1.5 rounded-full text-sm transition-colors',
                pathname === n.to
                  ? 'bg-[hsl(var(--accent-soft))] text-[hsl(var(--accent-strong))] font-medium'
                  : 'text-[hsl(var(--ink-soft))] hover:bg-[hsl(var(--surface-2))]'
              )}
            >
              {n.label}
            </Link>
          ))}
        </nav>

        {/* Search */}
        <form
          className="flex-1 max-w-md ml-auto relative"
          onSubmit={(e) => {
            e.preventDefault();
            if (q.trim()) navigate(`/home?q=${encodeURIComponent(q.trim())}`);
          }}
        >
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[hsl(var(--ink-faint))]" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="搜索：油皮面霜 / 户外背包 / 咖啡豆…"
            className="w-full h-9 pl-9 pr-4 rounded-full border border-[hsl(var(--line))] bg-[hsl(var(--surface))] text-sm placeholder:text-[hsl(var(--ink-faint))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--accent))] focus:ring-offset-2 shadow-soft transition-all"
          />
        </form>

        {/* Cart */}
        <Link to="/cart" className="relative shrink-0">
          <span className="h-9 w-9 rounded-full border border-[hsl(var(--line))] bg-[hsl(var(--surface))] flex items-center justify-center hover:bg-[hsl(var(--surface-2))] transition-colors">
            <ShoppingCart className="h-4 w-4 text-[hsl(var(--ink))]" />
          </span>
          {count > 0 && (
            <span className="absolute -top-1 -right-1 h-5 min-w-[20px] px-1 rounded-full bg-[hsl(var(--accent))] text-[#fff8ef] text-xs font-medium flex items-center justify-center cart-badge-bounce">
              {count}
            </span>
          )}
        </Link>

        {/* Auth */}
        {token ? (
          <div className="relative shrink-0 group">
            <span className="h-9 pl-2 pr-3 flex items-center gap-1.5 rounded-full border border-[hsl(var(--line))] bg-[hsl(var(--surface))] hover:bg-[hsl(var(--surface-2))] transition-colors">
              <span className="h-5 w-5 rounded-full bg-[hsl(var(--accent-soft))] flex items-center justify-center">
                <User className="h-3 w-3 text-[hsl(var(--accent-strong))]" />
              </span>
              <span className="text-sm text-[hsl(var(--ink))] max-w-[80px] truncate">{username}</span>
            </span>
            {/* pt-2 把"头像与菜单之间的空隙"并入悬浮区：鼠标移向退出按钮时菜单不会消失 */}
            <div className="absolute top-full right-0 pt-2 hidden group-hover:block">
              <button
                onClick={() => { logout(); navigate('/home'); }}
                className="block px-3 py-1.5 text-xs whitespace-nowrap text-[hsl(var(--ink-soft))] bg-[hsl(var(--surface))] border border-[hsl(var(--line))] rounded-[var(--radius-sm)] shadow-soft hover:bg-[hsl(var(--surface-2))]"
              >
                退出登录
              </button>
            </div>
          </div>
        ) : (
          <Link to="/login" className="shrink-0 px-3 py-1.5 rounded-full text-sm font-medium btn-warm">
            登录
          </Link>
        )}
      </div>
    </header>
  );
}
