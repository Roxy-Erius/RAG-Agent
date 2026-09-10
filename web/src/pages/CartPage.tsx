import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { cartApi, orderApi } from '../lib/api';
import { getSessionId } from '../lib/session';
import { useCartStore } from '../store/cart';
import { CartItem } from '../types';
import { productImageSrc } from '../lib/utils';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';
import { Trash2, Minus, Plus, ShoppingBag, ArrowRight } from 'lucide-react';

export function CartPage() {
  const navigate = useNavigate();
  const { items, setItems, updateQuantity, removeItem, clear } = useCartStore();
  const [loading, setLoading] = useState(true);
  const [checkingOut, setCheckingOut] = useState(false);
  const [orderId, setOrderId] = useState('');

  const sid = getSessionId();

  useEffect(() => {
    cartApi.list(sid).then((data) => {
      setItems(data.items || []);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [sid]);

  const checkedItems = items.filter((i) => i.checked !== false);
  const total = checkedItems.reduce((s, i) => s + (i.productPrice ?? 0) * i.quantity, 0);
  const totalCount = checkedItems.reduce((s, i) => s + i.quantity, 0);

  const handleCheckout = async () => {
    if (checkedItems.length === 0) return;
    setCheckingOut(true);
    try {
      const itemIds = checkedItems.map((i) => i.id);
      const res = await orderApi.checkout({ sessionId: sid, itemIds });
      setOrderId(res.orderId);
      // 从购物车移除已结算的
      itemIds.forEach((id) => removeItem(id));
    } catch (e) {
      alert(e instanceof Error ? e.message : '结算失败');
    } finally {
      setCheckingOut(false);
    }
  };

  // 改数量：乐观更新本地 + 同步后端
  const handleQty = async (id: number, q: number) => {
    updateQuantity(id, q);
    try {
      await cartApi.update(id, { sessionId: sid, quantity: q });
    } catch (e) {
      alert(e instanceof Error ? e.message : '改数量失败');
      cartApi.list(sid).then((data) => setItems(data.items || [])).catch(() => {});
    }
  };

  // 删除：乐观移除 + 同步后端
  const handleRemove = async (id: number) => {
    removeItem(id);
    try {
      await cartApi.remove(id, sid);
    } catch (e) {
      alert(e instanceof Error ? e.message : '删除失败');
      cartApi.list(sid).then((data) => setItems(data.items || [])).catch(() => {});
    }
  };

  // 清空：逐个删除后端条目后清空本地
  const handleClear = async () => {
    const ids = items.map((i) => i.id);
    try {
      await Promise.all(ids.map((id) => cartApi.remove(id, sid)));
      clear();
    } catch (e) {
      alert(e instanceof Error ? e.message : '清空失败');
      cartApi.list(sid).then((data) => setItems(data.items || [])).catch(() => {});
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-40" />
        {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24" />)}
      </div>
    );
  }

  return (
    <div className="space-y-5 anim-fade-in">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold text-[hsl(var(--ink))]">购物车</h1>
          <p className="text-sm text-[hsl(var(--ink-soft))] mt-1">共 {items.length} 件商品</p>
        </div>
        {items.length > 0 && (
          <Button variant="ghost" size="sm" onClick={handleClear}>清空</Button>
        )}
      </div>

      {orderId ? (
        <div className="card p-8 text-center space-y-4 anim-slide-up">
          <div className="h-14 w-14 mx-auto rounded-full bg-[hsl(var(--ok))]/15 flex items-center justify-center">
            <ShoppingBag className="h-7 w-7 text-[hsl(var(--ok))]" />
          </div>
          <div>
            <h2 className="font-semibold text-lg text-[hsl(var(--ink))]">结算成功</h2>
            <p className="text-sm text-[hsl(var(--ink-soft))] mt-1">订单号：{orderId}</p>
          </div>
          <div className="flex gap-3 justify-center">
            <Button variant="outline" onClick={() => navigate('/home')}>继续逛逛</Button>
            <Button onClick={() => navigate('/orders')}>查看订单 <ArrowRight className="h-4 w-4" /></Button>
          </div>
        </div>
      ) : items.length === 0 ? (
        <div className="card p-16 text-center space-y-4">
          <ShoppingBag className="h-12 w-12 mx-auto text-[hsl(var(--ink-faint))]" />
          <p className="text-[hsl(var(--ink-soft))]">购物车还是空的，去挑点好东西吧</p>
          <Button onClick={() => navigate('/home')}>去货架看看</Button>
        </div>
      ) : (
        <div className="grid lg:grid-cols-[1fr_320px] gap-5">
          {/* 列表 */}
          <div className="space-y-3">
            {items.map((item) => (
              <CartRow
                key={item.id}
                item={item}
                onQty={(q) => handleQty(item.id, q)}
                onRemove={() => handleRemove(item.id)}
              />
            ))}
          </div>

          {/* 结算卡 */}
          <div className="card p-5 space-y-4 h-fit sticky top-20">
            <h3 className="font-semibold text-[hsl(var(--ink))]">订单汇总</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between text-[hsl(var(--ink-soft))]">
                <span>商品件数</span>
                <span>{totalCount} 件</span>
              </div>
              <div className="flex justify-between text-[hsl(var(--ink-soft))]">
                <span>运费</span>
                <span>包邮</span>
              </div>
              <div className="border-t border-[hsl(var(--line))] pt-3 flex justify-between font-semibold text-[hsl(var(--ink))]">
                <span>合计</span>
                <span className="price text-xl">¥{total.toFixed(2)}</span>
              </div>
            </div>
            <Button size="lg" className="w-full gap-2" onClick={handleCheckout} disabled={checkingOut || totalCount === 0}>
              {checkingOut ? '结算中…' : `结算 ¥${total.toFixed(2)}`}
              <ArrowRight className="h-4 w-4" />
            </Button>
            <p className="text-xs text-[hsl(var(--ink-faint))] text-center">
              支持 7 天无理由退换 · 买手助理全程护航
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function CartRow({ item, onQty, onRemove }: { item: CartItem; onQty: (q: number) => void; onRemove: () => void }) {
  const img = productImageSrc({ imageBase64: item.productImageBase64 });
  return (
    <div className="card p-3 flex gap-3 items-center">
      <div className="h-16 w-16 rounded-[var(--radius-sm)] overflow-hidden bg-[hsl(var(--surface-2))] shrink-0">
        {img ? <img src={img} className="h-full w-full object-cover" /> : <div className="h-full w-full flex items-center justify-center text-xs text-[hsl(var(--ink-faint))]">无图</div>}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-[hsl(var(--ink))] clip-2">{item.productTitle || item.productId}</p>
        <div className="flex items-center gap-2 mt-1">
          {item.skuLabel && <span className="text-xs text-[hsl(var(--ink-faint))]">{item.skuLabel}</span>}
          <span className="price text-base">¥{(item.productPrice ?? 0).toFixed(2)}</span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="icon" className="h-7 w-7" onClick={() => onQty(Math.max(1, item.quantity - 1))} disabled={item.quantity <= 1}>
          <Minus className="h-3 w-3" />
        </Button>
        <span className="w-8 text-center text-sm font-medium">{item.quantity}</span>
        <Button variant="outline" size="icon" className="h-7 w-7" onClick={() => onQty(item.quantity + 1)}>
          <Plus className="h-3 w-3" />
        </Button>
        <Button variant="ghost" size="icon" className="h-7 w-7 text-[hsl(var(--danger))] hover:bg-[hsl(var(--danger))]/10" onClick={onRemove}>
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>
  );
}
