import { Suspense } from "react";
import PropertyEditView from "./PropertyEditView";

// Same cacheComponents/Suspense reasoning as ../page.tsx (see the comment
// there): this file only resolves the dynamic `id` param behind a Suspense
// boundary; all real behavior lives in the client component.
export default function PropertyEditPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <ResolvedPropertyEditView paramsPromise={params} />
    </Suspense>
  );
}

async function ResolvedPropertyEditView({ paramsPromise }: { paramsPromise: Promise<{ id: string }> }) {
  const { id } = await paramsPromise;
  return <PropertyEditView id={id} />;
}
