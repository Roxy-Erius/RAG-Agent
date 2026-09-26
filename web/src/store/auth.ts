import { create } from 'zustand';
import { authApi } from '../lib/api';
import { clearAuth, getToken, getUsername, saveAuth } from '../lib/auth';
import { getSessionId } from '../lib/session';
import { storeConversationId } from '../lib/conversation';

interface AuthState {
  token: string | null;
  username: string | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
  /** 登录成功后，把匿名 session 的购物车迁移到用户维度 */
  linkSession: (token?: string) => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: getToken(),
  username: getUsername(),
  loading: false,

  login: async (username, password) => {
    set({ loading: true });
    try {
      const res = await authApi.login({ username, password });
      saveAuth({ token: res.token, username });
      storeConversationId(null); // 换用户 → 清掉上一位的当前会话，避免串会话
      set({ token: res.token, username });
      await get().linkSession(res.token).catch((e) => console.warn('绑定会话失败', e));
    } finally {
      set({ loading: false });
    }
  },

  register: async (username, password) => {
    set({ loading: true });
    try {
      await authApi.register({ username, password });
    } finally {
      set({ loading: false });
    }
  },

  logout: () => {
    clearAuth();
    storeConversationId(null);
    set({ token: null, username: null });
  },

  linkSession: async (token) => {
    const t = token || get().token || getToken();
    if (!t) return;
    const sessionId = getSessionId();
    await authApi.linkSession(sessionId, t);
  },
}));
