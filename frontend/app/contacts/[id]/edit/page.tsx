import { Suspense } from "react";
import ContactEditView from "./ContactEditView";

// Same cacheComponents/Suspense reasoning as ../page.tsx.
export default function ContactEditPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <ResolvedContactEditView paramsPromise={params} />
    </Suspense>
  );
}

async function ResolvedContactEditView({ paramsPromise }: { paramsPromise: Promise<{ id: string }> }) {
  const { id } = await paramsPromise;
  return <ContactEditView id={id} />;
}
