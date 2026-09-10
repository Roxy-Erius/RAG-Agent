/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        bg: 'hsl(var(--bg))',
        'bg-soft': 'hsl(var(--bg-soft))',
        surface: 'hsl(var(--surface))',
        'surface-2': 'hsl(var(--surface-2))',
        ink: 'hsl(var(--ink))',
        'ink-soft': 'hsl(var(--ink-soft))',
        'ink-faint': 'hsl(var(--ink-faint))',
        line: 'hsl(var(--line))',
        accent: 'hsl(var(--accent))',
        'accent-strong': 'hsl(var(--accent-strong))',
        'accent-soft': 'hsl(var(--accent-soft))',
        ok: 'hsl(var(--ok))',
        warn: 'hsl(var(--warn))',
        danger: 'hsl(var(--danger))',
      },
      fontFamily: {
        sans: ['Noto Sans SC', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        display: ['Playfair Display', 'Noto Sans SC', 'serif'],
      },
      borderRadius: {
        DEFAULT: 'var(--radius)',
        lg: 'var(--radius-lg)',
        sm: 'var(--radius-sm)',
      },
      boxShadow: {
        soft: 'var(--shadow)',
        lift: 'var(--shadow-lg)',
      },
      spacing: {
        '4.5': '1.125rem',
        '18': '4.5rem',
        '22': '5.5rem',
        '30': '7.5rem',
      },
      animation: {
        'fade-in': 'fadeIn 0.25s ease both',
        'slide-up': 'slideUp 0.3s ease both',
        'slide-down': 'slideDown 0.3s ease both',
        'scale-in': 'scaleIn 0.25s ease both',
        'bounce-in': 'bounceIn 0.35s cubic-bezier(0.68, -0.55, 0.27, 1.55)',
      },
    },
  },
  plugins: [],
}
