// ===== 商品 =====
export interface Product {
  productId: string;
  title: string;
  brand?: string;
  category?: string;
  subCategory?: string;
  basePrice: number;
  imagePath?: string;
  imageBase64?: string; // data URL，可直接渲染
  marketingDescription?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ===== SKU =====
export interface Sku {
  skuId?: string;
  productId?: string;
  label?: string;      // 如 "40ml" / "M 码"
  price?: number;
  stock?: number;
}

// ===== 评价 / FAQ =====
export interface Review {
  id?: number;
  productId?: string;
  rating?: number;
  content?: string;
  userName?: string;
  createdAt?: string;
}

export interface Faq {
  id?: number;
  productId?: string;
  question?: string;
  answer?: string;
}

// ===== 购物车 =====
export interface CartItem {
  id: number;
  productId: string;
  productTitle?: string;
  productPrice?: number;
  productImageBase64?: string;
  productImagePath?: string;
  skuId?: string;
  skuLabel?: string;
  quantity: number;
  checked?: boolean;
}

// ===== 订单 =====
export interface Order {
  id?: number;
  orderId: string;
  userId?: number;
  sessionId: string;
  totalAmount: number;
  itemCount: number;
  status: string;      // paid / pending / cancelled
  createdAt?: string;
  paidAt?: string;
  items?: CartItem[];
}

// ===== SSE 事件 =====
export type SSEEventType = 'token' | 'product' | 'add_to_cart' | 'delete_from_cart' | 'clear_cart' | 'done' | 'error';

export interface SSEEvent {
  type: SSEEventType;
  content?: string;
  productId?: string;
  quantity?: number;
  mode?: 'add' | 'set';
  skuLabel?: string;
  cartItemId?: number;
  conversationId?: string;
  message?: string;
}

// ===== 聊天消息 =====
export interface ChatMessage {
  id: string;                    // uuid
  role: 'user' | 'assistant';
  text: string;
  cards?: Product[];             // 该消息附带的商品卡片
  cartActions?: { type: 'add' | 'set' | 'delete' | 'clear'; productId?: string; label?: string; quantity?: number; cartItemId?: number; done?: boolean }[];
  streaming?: boolean;           // 是否还在流式中
  timestamp: number;
}

// ===== 分页响应 =====
export interface PageResp<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

// ===== 通用列表响应（购物车/订单） =====
export interface ListResp<T> {
  items: T[];
  total: number;
}
