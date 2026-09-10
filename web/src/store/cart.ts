import { create } from 'zustand';
import { CartItem } from '../types';

interface CartState {
  items: CartItem[];
  addItem: (item: CartItem) => void;
  removeItem: (id: number) => void;
  setItems: (items: CartItem[]) => void;
  updateQuantity: (id: number, quantity: number) => void;
  clear: () => void;
  count: () => number;
  total: () => number;
}

export const useCartStore = create<CartState>((set, get) => ({
  items: [],
  addItem: (item) =>
    set((state) => {
      const idx = state.items.findIndex((i) => i.id === item.id);
      if (idx >= 0) {
        const next = [...state.items];
        next[idx] = item;
        return { items: next };
      }
      return { items: [...state.items, item] };
    }),
  removeItem: (id) => set((state) => ({ items: state.items.filter((i) => i.id !== id) })),
  setItems: (items) => set({ items }),
  updateQuantity: (id, quantity) =>
    set((state) => ({
      items: state.items.map((i) => (i.id === id ? { ...i, quantity } : i)),
    })),
  clear: () => set({ items: [] }),
  count: () => get().items.reduce((sum, i) => sum + i.quantity, 0),
  total: () => get().items.reduce((sum, i) => sum + (i.productPrice ?? 0) * i.quantity, 0),
}));
