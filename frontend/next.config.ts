import path from "node:path";
import type { NextConfig } from "next";

// Pin the project root to this folder. Without it, Next.js walks up and
// picks a stray pnpm-lock.yaml in a parent directory as the workspace root.
const projectRoot = path.resolve(__dirname);

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    root: projectRoot,
  },
  outputFileTracingRoot: projectRoot,
};

export default nextConfig;
