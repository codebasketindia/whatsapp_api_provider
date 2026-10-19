import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import { FullPageLoader } from '../components/ui/Spinner';

/** Requires a logged-in user; forces the password change screen when required. */
export default function ProtectedRoute({ allowPasswordChange = false }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageLoader />;
  if (status !== 'authenticated') {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (user?.mustChangePassword && !allowPasswordChange) return <Navigate to="/change-password" replace />;
  return <Outlet />;
}
