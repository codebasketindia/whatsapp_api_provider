import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { LogIn } from 'lucide-react';
import { useAuth } from '../../../auth/useAuth';
import { homePathFor } from '../../../auth/AuthProvider';
import { getErrorCode, getErrorMessage } from '../../../api/axios';
import { session } from '../../../lib/storage';
import { formatDateTime } from '../../../lib/format';
import { Alert, Button, FullPageLoader, Input, PasswordInput } from '../../../components/ui';
import AuthShell from '../components/AuthShell';

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email').email('Enter a valid email'),
  password: z.string().min(1, 'Enter your password'),
});

export const TWO_FA_KEY = 'cbwa.2fa';

/** Only allow in-app paths for ?next= to avoid open redirects. */
export function safeNext(next) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : null;
}

export default function LoginPage() {
  const { login, status, user } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  useEffect(() => {
    if (params.get('reset') === '1') setError(null);
  }, [params]);

  if (status === 'loading') return <FullPageLoader />;
  if (status === 'authenticated' && user) return <Navigate to={homePathFor(user)} replace />;

  const onSubmit = async (values) => {
    setError(null);
    try {
      const res = await login(values.email.trim().toLowerCase(), values.password);
      if (res.twoFactorRequired) {
        session.set(TWO_FA_KEY, JSON.stringify({ challengeId: res.challengeId, maskedEmail: res.maskedEmail, next: params.get('next') }));
        navigate('/login/verify');
        return;
      }
      const next = safeNext(params.get('next'));
      const home = homePathFor(res.user);
      const sameArea = next && ((res.user.userType === 'ADMIN' && next.startsWith('/admin')) || (res.user.userType === 'PARTNER' && next.startsWith('/partner')));
      navigate(res.user.mustChangePassword ? '/change-password' : sameArea ? next : home, { replace: true });
    } catch (err) {
      const code = getErrorCode(err);
      if (code === 'AUTH_ACCOUNT_LOCKED') {
        const until = err.response?.data?.data?.lockedUntil;
        setError(`Too many wrong attempts. Your account is locked${until ? ` until ${formatDateTime(until)}` : ' for a few minutes'}.`);
      } else {
        setError(getErrorMessage(err));
      }
    }
  };

  return (
    <AuthShell title="Log in" subtitle="Welcome back. Log in to manage your WhatsApp messaging.">
      {params.get('reset') === '1' && (
        <Alert variant="success" className="mb-4">
          Password changed. Please log in with your new password.
        </Alert>
      )}
      {error && (
        <Alert variant="error" className="mb-4">
          {error}
        </Alert>
      )}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <Input label="Email" type="email" autoComplete="username" inputMode="email" autoFocus error={errors.email} {...register('email')} />
        <PasswordInput label="Password" autoComplete="current-password" error={errors.password} {...register('password')} />
        <div className="flex justify-end">
          <Link to="/forgot-password" className="inline-flex min-h-[36px] items-center text-sm font-medium text-brand-700 hover:underline dark:text-brand-300">
            Forgot password?
          </Link>
        </div>
        <Button type="submit" fullWidth size="lg" loading={isSubmitting} leftIcon={LogIn}>
          Log in
        </Button>
      </form>
    </AuthShell>
  );
}
