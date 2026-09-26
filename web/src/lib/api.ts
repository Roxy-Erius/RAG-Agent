import axios, { AxiosInstance } from 'axios';
import { getSessionId } from './session';
import { getToken } from './auth';

const baseURL = import.meta.env.VITE_API_BASE || '/api';

export const api: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// 请求拦截：已登录带 Authorization: Bearer，否则带 X-Session-Id（匿名）
api.interceptors.request.use((config) => {
  config.headers = config.headers || {};
  const token = getToken();
  const sessionId = getSessionId();
  if (token && !config.headers.Authorization) {
    (config.headers as Record<string, string>).Authorization = `Bearer ${token}`;
  } else if (!config.headers.Authorization) {
    (config.headers as Record<string, string>)['X-Session-Id'] = sessionId;
  }
  return config;
});

// 响应拦截：统一错误处理
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err?.response?.data?.error || err?.message || '网络异常';
    return Promise.reject(new Error(msg));
  }
);

/** 后端 SKU 的 properties 是 JSON 字符串（如 {"容量":"30ml 经典装"}），拆成展示标签 */
function skuLabelFromProperties(props?: string): string {
  if (!props) return '';
  try {
    const obj = JSON.parse(props) as Record<string, unknown>;
    return Object.values(obj).join(' ');
  } catch {
    return props;
  }
}

// ===== 业务接口 =====
export const productApi = {
  list: (page = 1, size = 20) =>
    api.get<{ items: import('../types').Product[]; total: number; page: number; size: number; totalPages: number }>(
      '/products', { params: { page, size } }
    ).then((r) => r.data),
  detail: (id: string) =>
    api.get<import('../types').Product>(`/products/${id}`).then((r) => r.data),
  search: (query: string, topK = 10) =>
    api.get<import('../types').Product[]>(`/products/search`, { params: { query, topK } }).then((r) => r.data),
  skus: (id: string) =>
    api.get<Array<{ sku_id?: string; product_id?: string; price?: number; properties?: string }>>(`/products/${id}/skus`)
      .then((r) => r.data.map((s) => ({
        skuId: s.sku_id ?? '',
        productId: s.product_id,
        price: s.price,
        label: skuLabelFromProperties(s.properties),
      }))),
  reviews: (id: string) =>
    api.get<Array<{ id?: number; rating?: number; content?: string; nickname?: string }>>(`/products/${id}/reviews`)
      .then((r) => r.data.map((v) => ({
        id: v.id,
        rating: v.rating,
        content: v.content,
        userName: v.nickname,
      }))),
  faqs: (id: string) =>
    api.get<import('../types').Faq[]>(`/products/${id}/faqs`).then((r) => r.data),
};

export const cartApi = {
  // 后端 CartController 用 @RequestParam（query 参数），不是 JSON body，故用 { params } 传参
  list: (sessionId: string) =>
    api.get<import('../types').CartItem[]>(`/cart`, { params: { sessionId } })
      .then((r) => ({ items: r.data })),
  add: (body: { sessionId: string; productId: string; quantity?: number; skuId?: string; skuLabel?: string }) =>
    api.post<import('../types').CartItem>(`/cart/add`, null, { params: body }).then((r) => r.data),
  set: (body: { sessionId: string; productId: string; quantity: number }) =>
    api.post(`/cart/set`, null, { params: body }).then((r) => r.data),
  update: (id: number, body: { sessionId: string; quantity: number }) =>
    api.put(`/cart/${id}`, null, { params: body }).then((r) => r.data),
  remove: (id: number, sessionId: string) =>
    api.delete(`/cart/${id}`, { params: { sessionId } }).then((r) => r.data),
};

export const orderApi = {
  checkout: (body: { sessionId: string; itemIds: number[] }) =>
    api.post<{ orderId: string; total: number; itemCount: number; status: string; paidAt: string }>(`/orders/checkout`, body).then((r) => r.data),
  list: (sessionId: string) =>
    api.get<{ items: import('../types').Order[]; total: number }>(`/orders`, { params: { sessionId } }).then((r) => r.data),
};

export const authApi = {
  login: (body: { username: string; password: string }) =>
    api.post<{ token: string }>(`/auth/login`, body).then((r) => r.data),
  register: (body: { username: string; password: string }) =>
    api.post(`/auth/register`, body).then((r) => r.data),
  linkSession: (sessionId: string, token: string) =>
    api.post(`/auth/link-session?sessionId=${sessionId}`, null, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.data),
};

export const behaviorApi = {
  report: (body: { sessionId: string; type: string; productId?: string }) =>
    api.post(`/behaviors`, body).then((r) => r.data).catch(() => {}), // fire-and-forget
};

export const conversationApi = {
  list: (q = '') =>
    api.get<import('../types').Conversation[]>(`/conversations`, { params: { q } }).then((r) => r.data),
  messages: (id: string) =>
    api.get<import('../types').HistoryMessage[]>(`/conversations/${id}/messages`).then((r) => r.data),
  remove: (id: string) =>
    api.delete(`/conversations/${id}`).then((r) => r.data),
};
