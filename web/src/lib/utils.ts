import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** 商品图片：优先 imageBase64（data URL 直渲），否则拼后端 /images 前缀 */
export function productImageSrc(p: { imageBase64?: string; imagePath?: string }): string {
  if (p.imageBase64) return p.imageBase64;
  if (p.imagePath) {
    const base = (import.meta.env.VITE_API_BASE || '/api');
    // imagePath 形如 "1_美妆护肤/images/xxx.jpg"，需要拼成 /images/1_美妆护肤/images/xxx.jpg
    const origin = window.location.origin;
    return `${origin}${base.replace(/\/$/, '')}/images/${p.imagePath}`;
  }
  return '';
}
