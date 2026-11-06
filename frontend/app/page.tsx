import { fetchHealth } from "../lib/health";

export default async function Page() {
  const { backend, database } = await fetchHealth();

  return (
    <main>
      <p>Backend: {backend}</p>
      <p>Database: {database}</p>
    </main>
  );
}
