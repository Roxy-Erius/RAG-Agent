import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { productApi, cartApi } from '../lib/api';
import { getSessionId } from '../lib/session';
import { Product, Sku, Review, Faq } from '../types';
import { productImageSrc } from '../lib/utils';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Skeleton } from '../components/ui/skeleton';
import { ArrowLeft, ShoppingCart, Minus, Plus, ChevronDown, ChevronUp } from 'lucide-react';
import { useCartStore } from '../store/cart';

export function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const addToCart = useCartStore((s) => s.addItem);

  const [product, setProduct] = useState<Product | null>(null);
  const [skus, setSkus] = useState<Sku[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [faqs, setFaqs] = useState<Faq[]>([]);
  const [loading, setLoading] = useState(true);
  const [qty, setQty] = useState(1);
  const [activeSku, setActiveSku] = useState<string>('');
  const [openFaq, setOpenFaq] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      productApi.detail(id),
      productApi.skus(id).catch(() => []),
      productApi.reviews(id).catch(() => []),
      productApi.faqs(id).catch(() => []),
    ]).then(([p, s, r, f]) => {
      setProduct(p);
      setSkus(s);
      setReviews(r);
      setFaqs(f);
      setLoading(false);
    });
  }, [id]);

  const handleAdd = async () => {
    if (!product) return;
    setAdding(true);
    try {
      const sid = getSessionId();
      const item = await cartApi.add({ sessionId: sid, productId: product.productId, quantity: qty, skuId: activeSku || undefined });
      addToCart({ ...item, productTitle: product.title, productPrice: product.basePrice, productImageBase64: product.imageBase64 });
      setAdded(true);
      setTimeout(() => setAdded(false), 1800);
    } catch (e) {
      alert(e instanceof Error ? e.message : '加购失败');
    } finally {
      setAdding(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-40" />
        <div className="grid md:grid-cols-2 gap-6">
          <Skeleton className="aspect-square rounded-[var(--radius-lg)]" />
          <div className="space-y-3">
            <Skeleton className="h-8 w-3/4" />
            <Skeleton className="h-6 w-1/2" />
            <Skeleton className="h-24" />
          </div>
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="text-center py-20 space-y-3">
        <p className="text-[hsl(var(--ink-soft))]">商品不存在</p>
        <Button onClick={() => navigate('/home')}>回到货架</Button>
      </div>
    );
  }

  const img = productImageSrc(product);
  const price = skus.find((s) => s.skuId === activeSku)?.price ?? product.basePrice;

  return (
    <div className="space-y-6 anim-fade-in">
      {/* 返回 */}
      <Button variant="ghost" onClick={() => navigate(-1)} className="gap-1 -ml-2">
        <ArrowLeft className="h-4 w-4" />
        返回
      </Button>

      {/* 主体 */}
      <div className="grid md:grid-cols-2 gap-8">
        {/* 图 */}
        <div className="space-y-3">
          <div className="aspect-square rounded-[var(--radius-lg)] overflow-hidden bg-[hsl(var(--surface-2))] border border-[hsl(var(--line))]">
            {img ? (
              <img src={img} alt={product.title} className="h-full w-full object-cover" />
            ) : (
              <div className="h-full w-full flex items-center justify-center text-[hsl(var(--ink-faint))]">无图</div>
            )}
          </div>
          {skus.length > 0 && (
            <div className="flex gap-2 overflow-x-auto pb-1">
              {skus.map((s) => (
                <button
                  key={s.skuId}
                  onClick={() => setActiveSku(s.skuId === activeSku ? '' : (s.skuId || ''))}
                  className={`shrink-0 px-3 py-2 rounded-[var(--radius-sm)] text-sm border transition-all ${
                    activeSku === s.skuId
                      ? 'border-[hsl(var(--accent))] bg-[hsl(var(--accent-soft))] text-[hsl(var(--accent-strong))] font-medium'
                      : 'border-[hsl(var(--line))] bg-[hsl(var(--surface))] text-[hsl(var(--ink))] hover:border-[hsl(var(--accent))]'
                  }`}
                >
                  {s.label || s.skuId}
                  {s.price != null && <span className="text-xs text-[hsl(var(--ink-faint))] ml-1">¥{s.price.toFixed(2)}</span>}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* 信息 */}
        <div className="space-y-5">
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              {product.brand && <Badge variant="accent">{product.brand}</Badge>}
              {product.category && <Badge variant="outline">{product.category}</Badge>}
              {product.subCategory && <Badge variant="outline">{product.subCategory}</Badge>}
            </div>
            <h1 className="font-display text-2xl font-bold text-[hsl(var(--ink))] leading-snug">
              {product.title}
            </h1>
            {product.marketingDescription && (
              <p className="text-sm text-[hsl(var(--ink-soft))] leading-relaxed">{product.marketingDescription}</p>
            )}
          </div>

          {/* 价格 + 数量 + 加购 */}
          <div className="card p-4 space-y-4">
            <div className="flex items-end justify-between">
              <div className="price text-3xl">
                <span className="text-sm align-top">¥</span>
                {price.toFixed(2)}
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="icon" onClick={() => setQty(Math.max(1, qty - 1))} disabled={qty <= 1}>
                  <Minus className="h-3.5 w-3.5" />
                </Button>
                <span className="w-10 text-center font-medium">{qty}</span>
                <Button variant="outline" size="icon" onClick={() => setQty(qty + 1)}>
                  <Plus className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
            <Button size="lg" className="w-full gap-2" onClick={handleAdd} disabled={adding}>
              <ShoppingCart className="h-4 w-4" />
              {adding ? '加入中…' : added ? '已加入 ✓' : '加入购物车'}
            </Button>
          </div>

          {/* FAQ */}
          {faqs.length > 0 && (
            <div className="space-y-2">
              <h3 className="font-semibold text-sm text-[hsl(var(--ink))]">常见问题</h3>
              <div className="space-y-2">
                {faqs.slice(0, 5).map((f, i) => (
                  <div key={i} className="card p-3">
                    <button
                      className="flex items-center justify-between w-full text-left text-sm font-medium text-[hsl(var(--ink))]"
                      onClick={() => setOpenFaq(openFaq === i ? null : i)}
                    >
                      {f.question}
                      {openFaq === i ? <ChevronUp className="h-4 w-4 text-[hsl(var(--ink-faint))]" /> : <ChevronDown className="h-4 w-4 text-[hsl(var(--ink-faint))]" />}
                    </button>
                    {openFaq === i && <p className="text-sm text-[hsl(var(--ink-soft))] mt-2 leading-relaxed">{f.answer}</p>}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 评价 */}
      {reviews.length > 0 && (
        <div className="space-y-3 pt-4 border-t border-[hsl(var(--line))]">
          <h3 className="font-semibold text-sm text-[hsl(var(--ink))]">用户评价 ({reviews.length})</h3>
          <div className="grid sm:grid-cols-2 gap-3">
            {reviews.slice(0, 6).map((r, i) => (
              <div key={i} className="card p-3 space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-[hsl(var(--ink-faint))]">{r.userName || '匿名'}</span>
                  <span className="text-xs text-[hsl(var(--accent))]">{r.rating ? '★'.repeat(r.rating) : ''}</span>
                </div>
                <p className="text-sm text-[hsl(var(--ink))] leading-relaxed clip-2">{r.content}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
