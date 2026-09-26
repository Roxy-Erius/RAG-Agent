import axios from 'axios';
import { clearToken, getToken } from './auth';

export const api = axios.create({ baseURL: '/api', timeout: 30000 });

// 请求拦截：带 Bearer token
api.interceptors.request.use((config) => {
  const t = getToken();
  if (t) (config.headers as Record<string, string>).Authorization = `Bearer ${t}`;
  return config;
});

// 响应拦截：401/403 → 清 token 回登录页
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err?.response?.status;
    if (status === 401 || status === 403) {
      clearToken();
      if (window.location.pathname !== '/login') window.location.href = '/login';
    }
    const msg = err?.response?.data?.error || err?.message || '网络异常';
    return Promise.reject(new Error(msg));
  }
);

// ==================== 类型 ====================
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface Overview {
  userCount: number;
  conversationCount: number;
  messageCount: number;
  orderCount: number;
  gmv: number;
  productCount: number;
  behaviorCount: number;
  todayNewUsers: number;
  todayConversations: number;
  todayOrders: number;
}

export interface AdminUser {
  id: number;
  username: string;
  role: string;
  createdAt?: string;
  conversationCount: number;
  orderCount: number;
}

export interface AdminConversation {
  id: number;
  conversationId: string;
  userId?: number;
  username?: string;
  title?: string;
  messageCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminMessage {
  id: number;
  conversationId: number;
  role: string;
  content: string;
  productIds?: string | null;
  createdAt?: string;
}

export interface AdminOrder {
  id: number;
  orderId: string;
  userId?: number;
  sessionId: string;
  totalAmount: number;
  itemCount: number;
  status: string;
  createdAt?: string;
  paidAt?: string;
}

export interface AdminBehavior {
  id: number;
  userId: number;
  productId: string;
  actionType: string;
  createdAt?: string;
}

export interface TimeseriesPoint {
  date: string;
  newUsers: number;
  conversations: number;
  orders: number;
}

export interface LogEvent {
  id: number;
  ts: string;
  level: string;
  category: string;
  logger?: string;
  message?: string;
  thread?: string;
}

export interface JudgeRun {
  id: number;
  runAt: string;
  generatorModel?: string;
  judgeModel?: string;
  caseCount: number;
  avgFaithfulness: number;
  avgRelevance: number;
  avgTone: number;
  note?: string;
}

export interface JudgeCase {
  id: number;
  runId: number;
  query: string;
  reply: string;
  faithfulness: number;
  relevance: number;
  tone: number;
  elapsedMs: number;
}

// ==================== 接口 ====================
export const authApi = {
  login: (body: { username: string; password: string }) =>
    api.post<{ token: string; username: string; role: string }>('/auth/login', body).then((r) => r.data),
};

export const adminApi = {
  overview: () => api.get<Overview>('/admin/overview').then((r) => r.data),
  users: (p: { page: number; size: number; q?: string }) =>
    api.get<PageResult<AdminUser>>('/admin/users', { params: p }).then((r) => r.data),
  conversations: (p: { page: number; size: number; userId?: number; q?: string }) =>
    api.get<PageResult<AdminConversation>>('/admin/conversations', { params: p }).then((r) => r.data),
  messages: (conversationId: string) =>
    api.get<AdminMessage[]>(`/admin/conversations/${conversationId}/messages`).then((r) => r.data),
  orders: (p: { page: number; size: number }) =>
    api.get<PageResult<AdminOrder>>('/admin/orders', { params: p }).then((r) => r.data),
  behaviors: (p: { page: number; size: number; actionType?: string }) =>
    api.get<PageResult<AdminBehavior>>('/admin/behaviors', { params: p }).then((r) => r.data),
  timeseries: (days: number) =>
    api.get<TimeseriesPoint[]>('/admin/metrics/timeseries', { params: { days } }).then((r) => r.data),
  logs: (p: { page: number; size: number; category?: string; level?: string; logger?: string; q?: string }) =>
    api.get<PageResult<LogEvent>>('/admin/logs', { params: p }).then((r) => r.data),
  judgeRuns: (p: { page: number; size: number }) =>
    api.get<PageResult<JudgeRun>>('/admin/judge/runs', { params: p }).then((r) => r.data),
  judgeCases: (runId: number) =>
    api.get<JudgeCase[]>(`/admin/judge/runs/${runId}/cases`).then((r) => r.data),
};
