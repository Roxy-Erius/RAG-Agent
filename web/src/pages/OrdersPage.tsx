import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { orderApi } from '../lib/api';
import { getSessionId } from '../lib/session';
import { Order } from '../types';
import { productImageSrc } from '../lib/utils';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';
import { Package, ChevronRight, Clock, CheckCircle2 } from 'lucide-react';

export function OrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const sid = getSessionId();
    orderApi.list(sid).then((data) => {
      setOrders(data.items || []);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-40" />
        {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-28" />)}
      </div>
    );
  }

  return (
    <div className="space-y-5 anim-fade-in">
      <div>
        <h1 className="font-display text-2xl font-bold text-[hsl(var(--ink))]">我的订单</h1>
        <p className="text-sm text-[hsl(var(--ink-soft))] mt-1">共 {orders.length} 笔订单</p>
      </div>

      {orders.length === 0 ? (
        <div className="card p-16 text-center space-y-4">
          <Package className="h-12 w-12 mx-auto text-[hsl(var(--ink-faint))]" />
          <p className="text-[hsl(var(--ink-soft))]">还没有订单，去逛逛吧</p>
          <Button onClick={() => navigate('/home')}>去货架看看</Button>
        </div>
      ) : (
        <div className="space-y-3">
          {orders.map((o) => (
            <OrderCard key={o.orderId} order={o} onClick={() => navigate(`/orders/${o.orderId}`)} />
          ))}
        </div>
      )}
    </div>
  );
}

function OrderCard({ order, onClick }: { order: Order; onClick: () => void }) {
  const items = order.items || [];
  const preview = items.slice(0, 3);

  return (
    <div className="card p-4 cursor-pointer hover:shadow-lift transition-shadow" onClick={onClick}>
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-[var(--radius-sm)] bg-[hsl(var(--surface-2))] flex items-center justify-center shrink-0">
            <Package className="h-5 w-5 text-[hsl(var(--ink-soft))]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-medium text-sm text-[hsl(var(--ink))]">订单 {order.orderId}</span>
              <Badge variant={order.status === 'paid' ? 'ok' : 'default'}>
                {order.status === 'paid' ? <CheckCircle2 className="h-3 w-3" /> : <Clock className="h-3 w-3" />}
                {order.status === 'paid' ? '已支付' : order.status}
              </Badge>
            </div>
            <p className="text-xs text-[hsl(var(--ink-faint))] mt-0.5">
              {order.paidAt ? new Date(order.paidAt).toLocaleString('zh-CN') : order.createdAt || ''} · {order.itemCount} 件商品
            </p>
          </div>
        </div>
        <div className="text-right shrink-0">
          <div className="price text-lg">¥{order.totalAmount.toFixed(2)}</div>
          <ChevronRight className="h-4 w-4 text-[hsl(var(--ink-faint))] ml-auto mt-1" />
        </div>
      </div>

      {/* 商品预览 */}
      {preview.length > 0 && (
        <div className="mt-3 pt-3 border-t border-[hsl(var(--line))] flex items-center gap-2">
          {preview.map((item, i) => (
            <div key={i} className="h-10 w-10 rounded-[var(--radius-sm)] overflow-hidden bg-[hsl(var(--surface-2))]">
              {item.productImageBase64 && <img src={productImageSrc({ imageBase64: item.productImageBase64 })} className="h-full w-full object-cover" />}
            </div>
          ))}
          {items.length > 3 && <span className="text-xs text-[hsl(var(--ink-faint))]">+{items.length - 3}</span>}
        </div>
      )}
    </div>
  );
}
