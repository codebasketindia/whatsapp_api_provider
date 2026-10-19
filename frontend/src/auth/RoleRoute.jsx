import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './useAuth';

/** Allows only the given userType (ADMIN | PARTNER); otherwise shows 403. */
export default function RoleRoute({ role, children }) {
  const { user } = useAuth();
  if (user?.userType !== role) return <Navigate to="/403" replace />;
  return children || <Outlet />;
}
