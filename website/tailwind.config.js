/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'brand': {
          DEFAULT: '#008800', // O.A.S Green
          hover: '#006600',
          subtle: 'rgba(0, 136, 0, 0.05)',
        },
        'ui': {
          bg: '#FFFFFF',
          surface: '#F9FAFB',
          border: '#E4E4E7',
          text: '#09090B',
          muted: '#71717A',
        }
      },
      fontFamily: {
        'sans': ['Inter', 'system-ui', 'sans-serif'],
        'mono': ['JetBrains Mono', 'monospace'],
      },
      boxShadow: {
        'brutalist': '4px 4px 0px 0px rgba(0, 0, 0, 1)',
        'soft': '0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px -1px rgba(0, 0, 0, 0.05)',
      }
    },
  },
  plugins: [],
}
