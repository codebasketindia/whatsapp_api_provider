import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { homePathFor } from '../auth/AuthProvider';
import { FullPageLoader } from '../components/ui/Spinner';

export default function RootRedirect() {
  const { status, user } = useAuth();
  if (status === 'loading') return <FullPageLoader />;
  return <Navigate to={status === 'authenticated' ? homePathFor(user) : '/login'} replace />;
}
