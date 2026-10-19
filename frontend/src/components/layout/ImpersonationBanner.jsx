import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { UserCog } from 'lucide-react';
import { useAuth } from '../../auth/useAuth';
import Button from '../ui/Button';

/** Shown while an admin is logged in as a partner. */
export default function ImpersonationBanner() {
  const { user, isImpersonating, exitImpersonation } = useAuth();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  if (!isImpersonating) return null;
  const exit = async () => {
    setBusy(true);
    const admin = await exitImpersonation();
    setBusy(false);
    if (admin) {
      toast.success('Back to your admin account');
      navigate(user?.partner?.uid ? '/admin/partners' : '/admin', { replace: true });
    } else {
      navigate('/login', { replace: true });
    }
  };
  return (
    <div className="sticky top-0 z-40 flex flex-wrap items-center gap-x-3 gap-y-2 bg-purple-700 px-4 py-2 text-sm text-white">
      <UserCog className="h-4 w-4 shrink-0" aria-hidden />
      <p className="min-w-0 flex-1">
        You are viewing as <strong>{user?.partner?.businessName || user?.name}</strong> (logged in by {user?.impersonatedBy?.name}). Every action is recorded.
      </p>
      <Button size="sm" variant="secondary" onClick={exit} loading={busy} className="!bg-white !text-purple-800">
        Exit
      </Button>
    </div>
  );
}
