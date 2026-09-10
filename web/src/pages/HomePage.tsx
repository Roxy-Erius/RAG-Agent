import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { productApi } from '../lib/api';
import { Product } from '../types';
import { ProductCard } from '../components/ProductCard';
import { Skeleton } from '../components/ui/skeleton';
import { Button } from '../components/ui/button';
import { ChevronLeft, ChevronRight, PackageSearch } from 'lucide-react';

const PAGE_SIZE = 12; // 展览式陈列，每页少而精

export function HomePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') || '';
  const page = Math.max(1, parseInt(searchParams.get('page') || '1', 10));

  const [products, setProducts] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    const load = async () => {
      try {
        if (q) {
          // 搜索走向量检索接口
          const items = await productApi.search(q, 40);
          setProducts(items);
          setTotal(items.length);
          setTotalPages(1);
        } else {
          const data = await productApi.list(page, PAGE_SIZE);
          setProducts(data.items);
          setTotal(data.total);
          setTotalPages(data.totalPages);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : '加载失败');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [page, q]);

  const setPage = (p: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', String(p));
      return next;
    });
  };

  return (
    <div className="space-y-5">
      {/* 页头 */}
      <div className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold text-[hsl(var(--ink))]">
            {q ? `搜索“${q}”` : '今日货架'}
          </h1>
          <p className="text-sm text-[hsl(var(--ink-soft))] mt-1">
            {q ? `找到 ${total} 件商品` : '每一件都由买手助理精选'}
          </p>
        </div>
        {!q && total > 0 && (
          <span className="text-xs text-[hsl(var(--ink-faint))]">
            第 {page} / {totalPages} 页 · 共 {total} 件
          </span>
        )}
      </div>

      {/* 内容 */}
      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-5">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="aspect-square rounded-[var(--radius)]" />
          ))}
        </div>
      ) : error ? (
        <div className="text-center py-16 space-y-3">
          <PackageSearch className="h-10 w-10 mx-auto text-[hsl(var(--ink-faint))]" />
          <p className="text-sm text-[hsl(var(--ink-soft))]">{error}</p>
          <Button variant="outline" onClick={() => window.location.reload()}>重试</Button>
        </div>
      ) : products.length === 0 ? (
        <div className="text-center py-16 space-y-3">
          <PackageSearch className="h-10 w-10 mx-auto text-[hsl(var(--ink-faint))]" />
          <p className="text-sm text-[hsl(var(--ink-soft))]">没找到相关商品，换个关键词试试</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-5 anim-fade-in">
            {products.map((p) => (
              <ProductCard key={p.productId} product={p} />
            ))}
          </div>

          {/* 分页 */}
          {!q && totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-4">
              <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
                <ChevronLeft className="h-4 w-4" />
                上一页
              </Button>
              <span className="text-sm text-[hsl(var(--ink-soft))] px-2">
                {page} / {totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>
                下一页
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
