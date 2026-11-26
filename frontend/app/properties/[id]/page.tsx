import { Suspense } from "react";
import PropertyDetailView from "./PropertyDetailView";

// This app's `cacheComponents: true` config (see next.config.ts) treats a
// dynamic route's `params` as runtime-only data when there's no
// generateStaticParams - reading it must happen behind a Suspense boundary,
// or the build fails. The resolver below is the only thing that awaits
// `params`; all real behavior (fetching, rendering, the Edit link, the
// translations/gallery) lives in PropertyDetailView, a plain client
// component that takes `id` as an ordinary prop and follows the same
// fetch-on-mount pattern as the rest of this app's pages (see
// app/properties/page.tsx) - server-side fetching here would need to
// manually forward the browser's session cookie, which nothing else in
// this app does.
export default function PropertyDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <ResolvedPropertyDetailView paramsPromise={params} />
    </Suspense>
  );
}

async function ResolvedPropertyDetailView({ paramsPromise }: { paramsPromise: Promise<{ id: string }> }) {
  const { id } = await paramsPromise;
  return <PropertyDetailView id={id} />;
}
