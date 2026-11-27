import { Suspense } from "react";
import ContactDetailView from "./ContactDetailView";

// Same cacheComponents/Suspense reasoning as properties/[id]/page.tsx: this
// file only resolves the dynamic `id` param behind a Suspense boundary;
// all real behavior lives in the client component.
export default function ContactDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <ResolvedContactDetailView paramsPromise={params} />
    </Suspense>
  );
}

async function ResolvedContactDetailView({ paramsPromise }: { paramsPromise: Promise<{ id: string }> }) {
  const { id } = await paramsPromise;
  return <ContactDetailView id={id} />;
}
