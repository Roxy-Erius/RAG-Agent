import { create } from 'zustand';

interface AgentContext {
  /** 当前页面的商品上下文（进入详情页时注入） */
  productId?: string;
  productTitle?: string;
  setContext: (p?: { productId?: string; productTitle?: string }) => void;
  clearContext: () => void;
}

export const useAgentContext = create<AgentContext>((set) => ({
  setContext: (p) => set({ productId: p?.productId, productTitle: p?.productTitle }),
  clearContext: () => set({ productId: undefined, productTitle: undefined }),
}));
