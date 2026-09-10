import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/auth';

interface RequireAuthProps {
  children: React.ReactNode;
  /** 允许匿名访问的页面（购物车用 sessionId 也可结算） */
  anon?: boolean;
}

/** 路由守卫：已登录放行；否则重定向到 /login（携带来源） */
export function RequireAuth({ children, anon = false }: RequireAuthProps) {
  const token = useAuthStore((s) => s.token);
  const location = useLocation();

  if (token) return <>{children}</>;
  if (anon) return <>{children}</>; // 匿名也放行（购物车走 sessionId）

  return <Navigate to="/login" state={{ from: location.pathname }} replace />;
}
