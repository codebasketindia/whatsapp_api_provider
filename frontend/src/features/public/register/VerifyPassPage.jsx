import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import * as regApi from '../../../api/endpoints/registrations';
import { Button, Spinner } from '../../../components/ui';
import { tokenStore } from '../../../auth/tokenStore';
import PassStatusCard from './PassStatusCard';

/** PV1: page opened by scanning a pass QR with any phone camera. Shows validity only; staff redeem in the dashboard. */
export default function VerifyPassPage() {
  const { token } = useParams();
  const q = useQuery({ queryKey: ['public', 'pass', token], queryFn: () => regApi.checkPass(token), retry: false });
  const loggedIn = Boolean(tokenStore.getRefreshToken());
  return (
    <div className="min-h-screen bg-[#fbf8f1] px-4 py-10">
      <div className="mx-auto max-w-md space-y-4">
        <h1 className="text-center text-2xl font-extrabold text-[#1e2a44]">Pass check</h1>
        {q.isLoading ? <div className="flex justify-center p-10"><Spinner /></div>
          : q.isError ? <PassStatusCard result={{ status: 'INVALID', message: 'This pass could not be checked' }} />
          : <PassStatusCard result={q.data} />}
        {loggedIn && <Button fullWidth to={`/partner/passes/verify?token=${encodeURIComponent(token)}`}>Open in scanner to mark as used</Button>}
      </div>
    </div>
  );
}
