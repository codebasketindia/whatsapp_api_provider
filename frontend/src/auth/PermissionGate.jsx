import { Navigate } from 'react-router-dom';
import { useAuth } from './useAuth';

/**
 * Renders children only if the admin has the permission (or any of an array).
 * `redirect` sends unauthorised users to /403 instead of rendering the fallback.
 */
export default function PermissionGate({ permission, children, fallback = null, redirect = false }) {
  const { hasPermission } = useAuth();
  if (hasPermission(permission)) return children;
  return redirect ? <Navigate to="/403" replace /> : fallback;
}
