/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'mc-green': '#10b981', // Emerald 500
        'mc-dark-green': '#059669', // Emerald 600
        'wiki-bg': '#f8fafc', // Slate 50
        'wiki-card': '#ffffff', // White
        'wiki-border': '#f1f5f9', // Slate 100
        'wiki-text': '#334155', // Slate 700
      },
      fontFamily: {
        'sans': ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        'soft-sm': '0 2px 4px 0 rgba(0,0,0,0.02), 0 1px 2px -1px rgba(0,0,0,0.02)',
        'soft-md': '0 4px 6px -1px rgba(0,0,0,0.03), 0 2px 4px -2px rgba(0,0,0,0.03)',
        'soft-xl': '0 20px 25px -5px rgba(0,0,0,0.03), 0 8px 10px -6px rgba(0,0,0,0.03)',
      }
    },
  },
  plugins: [],
}
