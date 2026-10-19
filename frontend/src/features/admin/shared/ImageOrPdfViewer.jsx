import { useEffect, useState } from 'react';
import { Download, FileText } from 'lucide-react';
import { api } from '../../../api/axios';
import { toApiPath } from '../../../api/endpoints/files';
import { Button, ErrorState, Spinner } from '../../../components/ui';

/** Loads a protected file (/api/files/...) with the bearer token and shows image or PDF inline. */
export default function ImageOrPdfViewer({ url, filename = 'file' }) {
  const [state, setState] = useState({ loading: true, src: null, type: null, error: null });
  useEffect(() => {
    let revoked = false;
    let objectUrl = null;
    if (!url) {
      setState({ loading: false, src: null, type: null, error: null });
      return undefined;
    }
    setState({ loading: true, src: null, type: null, error: null });
    api
      .get(toApiPath(url), { responseType: 'blob' })
      .then((res) => {
        objectUrl = URL.createObjectURL(res.data);
        if (!revoked) setState({ loading: false, src: objectUrl, type: res.data?.type || '', error: null });
      })
      .catch((error) => !revoked && setState({ loading: false, src: null, type: null, error }));
    return () => {
      revoked = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url]);

  if (!url) return <p className="text-sm text-muted">No file attached.</p>;
  if (state.loading) return <div className="flex justify-center p-6"><Spinner /></div>;
  if (state.error) return <ErrorState error={state.error} />;
  const isPdf = (state.type || '').includes('pdf') || /\.pdf$/i.test(filename);
  return (
    <div className="space-y-3">
      {isPdf ? (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-line p-6 text-center">
          <FileText className="h-10 w-10 text-muted" aria-hidden />
          <p className="text-sm text-muted">PDF document</p>
          <iframe title={filename} src={state.src} className="hidden h-[70vh] w-full rounded-lg md:block" />
        </div>
      ) : (
        <img src={state.src} alt={filename} className="mx-auto max-h-[70vh] w-auto rounded-lg border border-line object-contain" />
      )}
      <div className="flex justify-end">
        <Button variant="secondary" size="sm" leftIcon={Download} href={state.src} download={filename}>
          Open / download
        </Button>
      </div>
    </div>
  );
}
