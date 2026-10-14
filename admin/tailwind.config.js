/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--border))",
        ring: "hsl(var(--primary))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: { DEFAULT: "hsl(var(--primary))", foreground: "hsl(var(--primary-foreground))" },
        secondary: { DEFAULT: "hsl(var(--secondary))", foreground: "hsl(var(--foreground))" },
        muted: { DEFAULT: "hsl(var(--muted))", foreground: "hsl(var(--muted-foreground))" },
        accent: { DEFAULT: "hsl(var(--chip))", foreground: "hsl(var(--primary))" },
        destructive: { DEFAULT: "hsl(var(--destructive))", foreground: "0 0% 100%" },
        card: { DEFAULT: "hsl(var(--card))", foreground: "hsl(var(--foreground))" },
      },
      borderRadius: { lg: "12px", md: "8px", sm: "6px" },
    },
  },
  plugins: [],
};
