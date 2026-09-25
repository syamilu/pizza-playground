import next from "eslint-config-next";

export default [
  // Next.js recommended rules (Core Web Vitals)
  ...next(),
  // Project-specific ignores
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "dist/**",
    ],
  },
];
