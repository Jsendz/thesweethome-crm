import { Suspense } from "react";
import DealDetailView from "./DealDetailView";

// Same cacheComponents/Suspense reasoning as properties/[id]/page.tsx: this
// file only resolves the dynamic `id` param behind a Suspense boundary;
// all real behavior lives in the client component.
export default function DealDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <ResolvedDealDetailView paramsPromise={params} />
    </Suspense>
  );
}

async function ResolvedDealDetailView({ paramsPromise }: { paramsPromise: Promise<{ id: string }> }) {
  const { id } = await paramsPromise;
  return <DealDetailView id={id} />;
}
