import { Suspense } from "react";
import DealEditView from "./DealEditView";

// Same cacheComponents/Suspense reasoning as deals/[id]/page.tsx: this file
// only resolves the dynamic `id` param behind a Suspense boundary; all real
// behavior lives in the client component.
export default function DealEditPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <ResolvedDealEditView paramsPromise={params} />
    </Suspense>
  );
}

async function ResolvedDealEditView({ paramsPromise }: { paramsPromise: Promise<{ id: string }> }) {
  const { id } = await paramsPromise;
  return <DealEditView id={id} />;
}
