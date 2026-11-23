import { fetchHealth } from "../lib/health";

// This page reports live backend/database reachability, so its data can
// never be prerendered or cached without defeating its purpose. Opt the
// route out of Cache Components' instant-navigation validation rather than
// wrapping it in Suspense or "use cache" (see next.config.ts: cacheComponents).
export const instant = false;

export default async function Page() {
  const { backend, database } = await fetchHealth();

  return (
    <main>
      <p>Backend: {backend}</p>
      <p>Database: {database}</p>
    </main>
  );
}
