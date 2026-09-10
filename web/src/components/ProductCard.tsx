import { useNavigate } from 'react-router-dom';
import { Product } from '../types';
import { Card } from './ui/card';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import { productImageSrc } from '../lib/utils';
import { ShoppingCart } from 'lucide-react';

interface ProductCardProps {
  product: Product;
  /** 紧凑横向变体（Agent 聊天里的卡片用） */
  compact?: boolean;
  onClick?: () => void;
  onAdd?: (p: Product) => void;
}

export function ProductCard({ product, compact = false, onClick, onAdd }: ProductCardProps) {
  const navigate = useNavigate();
  const img = productImageSrc(product);
  const price = (product.basePrice ?? 0).toFixed(2);
  const [int, dec] = price.split('.');

  const go = () => (onClick ? onClick() : navigate(`/product/${product.productId}`));

  const handleAdd = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    onAdd?.(product);
  };

  // 聊天/紧凑变体：横向小图
  if (compact) {
    return (
      <Card hover onClick={go} className="cursor-pointer overflow-hidden group">
        <div className="flex gap-3 p-3">
          <div className="relative h-20 w-20 shrink-0 rounded-[var(--radius-sm)] overflow-hidden bg-[hsl(var(--surface-2))]">
            {img ? (
              <img src={img} alt={product.title} className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-300" />
            ) : (
              <div className="h-full w-full flex items-center justify-center text-[hsl(var(--ink-faint))] text-xs">无图</div>
            )}
            {product.subCategory && (
              <span className="absolute bottom-1 left-1 text-[10px] bg-[hsl(var(--ink))]/70 text-[#fff8ef] px-1.5 py-0.5 rounded">
                {product.subCategory}
              </span>
            )}
          </div>
          <div className="flex-1 min-w-0 flex flex-col justify-between py-0.5">
            <div className="space-y-1">
              <h4 className="text-sm font-medium text-[hsl(var(--ink))] clip-2 leading-snug group-hover:text-[hsl(var(--accent-strong))] transition-colors">
                {product.title}
              </h4>
              <div className="flex items-center gap-1.5">
                {product.brand && <Badge variant="outline">{product.brand}</Badge>}
                {product.category && <span className="text-xs text-[hsl(var(--ink-faint))]">{product.category}</span>}
              </div>
            </div>
            <div className="price text-lg">
              <span className="text-xs align-top">¥</span>
              {int}
              <span className="text-sm">.{dec}</span>
            </div>
          </div>
        </div>
      </Card>
    );
  }

  // 展览式卡片：图在上，信息在下，大方留白；正方形图满格贴合不裁切
  return (
    <Card hover onClick={go} className="cursor-pointer overflow-hidden group flex flex-col">
      {/* 图区（正方形，源图 800x800 完美贴合） */}
      <div className="relative aspect-square w-full overflow-hidden bg-[hsl(var(--surface-2))]">
        {img ? (
          <img
            src={img}
            alt={product.title}
            loading="lazy"
            className="h-full w-full object-cover group-hover:scale-[1.05] transition-transform duration-500 ease-out"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center text-[hsl(var(--ink-faint))] text-sm">无图</div>
        )}
        {/* 分类角标 */}
        {product.subCategory && (
          <span className="absolute top-2.5 left-2.5 text-[11px] bg-[hsl(var(--surface))]/85 text-[hsl(var(--ink))] px-2 py-0.5 rounded-full shadow-soft backdrop-blur">
            {product.subCategory}
          </span>
        )}
        {/* 加购浮层 */}
        <div className="absolute bottom-2.5 right-2.5 opacity-0 group-hover:opacity-100 translate-y-1 group-hover:translate-y-0 transition-all duration-300">
          <Button size="sm" onClick={handleAdd} className="gap-1 shadow-lift">
            <ShoppingCart className="h-3.5 w-3.5" />
            加购
          </Button>
        </div>
      </div>

      {/* 信息区（紧凑，价落差突出价格） */}
      <div className="flex-1 flex flex-col p-3 gap-1.5">
        <div className="flex items-center justify-between gap-1.5">
          <div className="flex items-center gap-1.5 min-w-0">
            {product.brand && <Badge variant="accent">{product.brand}</Badge>}
            {product.category && <span className="text-xs text-[hsl(var(--ink-faint))] truncate">{product.category}</span>}
          </div>
          <div className="price text-lg shrink-0">
            <span className="text-xs align-top">¥</span>
            {int}
            <span className="text-sm">.{dec}</span>
          </div>
        </div>
        <h4 className="font-medium text-sm leading-snug text-[hsl(var(--ink))] clip-2 group-hover:text-[hsl(var(--accent-strong))] transition-colors">
          {product.title}
        </h4>
      </div>
    </Card>
  );
}
