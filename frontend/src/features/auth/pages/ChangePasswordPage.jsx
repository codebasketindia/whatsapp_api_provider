import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../auth/useAuth';
import { homePathFor } from '../../../auth/AuthProvider';
import { Alert, Button } from '../../../components/ui';
import AuthShell from '../components/AuthShell';
import ChangePasswordForm from '../components/ChangePasswordForm';

/** Forced on first login or after an admin reset. */
export default function ChangePasswordPage() {
  const { user, refreshUser, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <AuthShell
      title="Choose a new password"
      subtitle="For your security, please set your own password before continuing."
      footer={
        <Button
          variant="link"
          onClick={async () => {
            await logout();
            navigate('/login', { replace: true });
          }}
        >
          Log out
        </Button>
      }
    >
      {user?.mustChangePassword && (
        <Alert variant="info" className="mb-4">
          You are using a temporary password.
        </Alert>
      )}
      <ChangePasswordForm
        submitLabel="Save and continue"
        onDone={async (updated) => {
          const me = updated || (await refreshUser());
          navigate(homePathFor({ ...me, mustChangePassword: false }), { replace: true });
        }}
      />
    </AuthShell>
  );
}
