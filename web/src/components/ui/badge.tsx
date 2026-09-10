import * as React from 'react';
import { cn } from '../../lib/utils';

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'accent' | 'outline' | 'ok' | 'danger';
}

function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return (
    <div
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        variant === 'default' && 'chip',
        variant === 'accent' && 'chip-accent',
        variant === 'outline' && 'border border-[hsl(var(--line))] text-[hsl(var(--ink-soft))] bg-transparent',
        variant === 'ok' && 'bg-[hsl(var(--ok))]/15 text-[hsl(var(--ok))]',
        variant === 'danger' && 'bg-[hsl(var(--danger))]/15 text-[hsl(var(--danger))]',
        className
      )}
      {...props}
    />
  );
}

export { Badge };
