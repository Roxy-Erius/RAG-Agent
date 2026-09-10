import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '../../lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-1.5 rounded-[var(--radius-sm)] font-medium transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[hsl(var(--accent))] focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98]',
  {
    variants: {
      variant: {
        default: 'btn-warm',
        secondary: 'bg-[hsl(var(--surface-2))] text-[hsl(var(--ink))] hover:bg-[hsl(var(--line))]',
        outline: 'border border-[hsl(var(--line))] bg-transparent hover:bg-[hsl(var(--surface-2))] text-[hsl(var(--ink))]',
        ghost: 'btn-ghost',
        link: 'text-[hsl(var(--accent))] underline-offset-4 hover:underline',
        danger: 'bg-[hsl(var(--danger))] text-[#fff8ef] hover:bg-[#9c3e3f] shadow-[0_2px_8px_-2px_rgba(176,73,74,0.4)]',
        ok: 'bg-[hsl(var(--ok))] text-[#fff8ef] hover:bg-[#3d6a4e] shadow-[0_2px_8px_-2px_rgba(74,124,93,0.4)]',
      },
      size: {
        default: 'h-9 px-4 text-sm',
        sm: 'h-7 px-2.5 text-xs',
        lg: 'h-11 px-5 text-base',
        icon: 'h-9 w-9',
        xs: 'h-6 px-2 text-xs',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => (
    <button className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />
  )
);
Button.displayName = 'Button';

export { Button, buttonVariants };
