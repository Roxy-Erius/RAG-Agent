import * as React from 'react';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';

export interface SheetProps {
  open: boolean;
  onClose: () => void;
  side?: 'right' | 'left';
  children: React.ReactNode;
  className?: string;
}

export function Sheet({ open, onClose, side = 'right', children, className }: SheetProps) {
  React.useEffect(() => {
    if (open) document.body.style.overflow = 'hidden';
    else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50">
      {/* backdrop */}
      <div
        className="absolute inset-0 bg-[hsl(var(--ink))]/30 backdrop-blur-[2px] transition-opacity duration-300"
        onClick={onClose}
      />
      {/* panel */}
      <div
        className={cn(
          'absolute top-0 h-full w-full max-w-sm bg-[hsl(var(--surface))] shadow-lift flex flex-col',
          side === 'right' ? 'right-0 animate-slide-up' : 'left-0 animate-slide-down',
          className
        )}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}

export function SheetHeader({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('flex items-center justify-between px-4 py-3 border-b border-[hsl(var(--line))]', className)}>{children}</div>;
}

export function SheetContent({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('flex-1 overflow-y-auto px-4 py-4', className)}>{children}</div>;
}

export function SheetFooter({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('border-t border-[hsl(var(--line))] px-4 py-3', className)}>{children}</div>;
}

export function SheetClose({ onClick, className }: { onClick: () => void; className?: string }) {
  return (
    <button
      onClick={onClick}
      className={cn('rounded-full p-1.5 hover:bg-[hsl(var(--surface-2))] transition-colors', className)}
    >
      <X className="h-4 w-4 text-[hsl(var(--ink-soft))]" />
    </button>
  );
}
