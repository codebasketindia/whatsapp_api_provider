import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import * as regApi from '../../../api/endpoints/registrations';
import { Spinner } from '../../../components/ui';

/** PR5: Terms & Conditions / Privacy Policy / Refund Policy of a booking link, each on its own page. */
export default function LegalPage() {
  const { slug, kind } = useParams();
  const q = useQuery({ queryKey: ['public', 'legal', slug, kind], queryFn: () => regApi.getLegal(slug, kind), retry: false });
  return (
    <div className="min-h-screen bg-[#fbf8f1] px-4 py-8 text-[#1f2937]">
      <article className="mx-auto max-w-2xl rounded-3xl border border-[#ece3cf] bg-white p-6 shadow-sm sm:p-8">
        <Link to={`/r/${slug}`} className="inline-flex min-h-tap items-center gap-1 text-sm font-medium text-[#1e2a44]"><ArrowLeft className="h-4 w-4" aria-hidden /> Back to booking</Link>
        {q.isLoading ? <div className="flex justify-center p-10"><Spinner /></div> : q.isError ? (
          <p className="mt-6 text-center text-gray-600">This page is not available.</p>
        ) : (
          <>
            {q.data.businessName && <p className="mt-4 text-xs font-semibold uppercase tracking-[0.2em] text-[#a8873d]">{q.data.businessName}</p>}
            <h1 className="mt-1 text-2xl font-extrabold text-[#1e2a44]">{q.data.title}</h1>
            <p className="text-sm text-gray-500">{q.data.formTitle}</p>
            <div className="mt-5 whitespace-pre-line text-[15px] leading-relaxed">{q.data.text}</div>
            <nav aria-label="Policies" className="mt-8 flex flex-wrap gap-x-4 gap-y-2 border-t border-[#ece3cf] pt-4 text-sm font-semibold">
              {[['terms', 'Terms & Conditions'], ['privacy', 'Privacy Policy'], ['refund', 'Refund Policy']].filter(([k]) => k !== kind).map(([k, label]) => (
                <Link key={k} to={`/r/${slug}/${k}`} className="text-[#0b4d2c] underline decoration-[#c9a24b] underline-offset-2">{label}</Link>
              ))}
            </nav>
          </>
        )}
      </article>
    </div>
  );
}
