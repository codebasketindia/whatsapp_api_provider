import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import toast from 'react-hot-toast';
import { changePassword } from '../../../api/endpoints/me';
import { getErrorMessage, getFieldErrors } from '../../../api/axios';
import { PASSWORD_HINT, PASSWORD_RULE } from '../../../lib/constants';
import { Button, PasswordInput } from '../../../components/ui';
import { useAuth } from '../../../auth/useAuth';

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z.string().regex(PASSWORD_RULE, PASSWORD_HINT),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { message: 'Passwords do not match', path: ['confirmPassword'] })
  .refine((v) => v.newPassword !== v.currentPassword, { message: 'New password must be different', path: ['newPassword'] });

/** Shared by the forced change-password screen and profile pages. */
export default function ChangePasswordForm({ onDone, submitLabel = 'Change password' }) {
  const { acceptTokens } = useAuth();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(schema), defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' } });

  const onSubmit = async ({ currentPassword, newPassword }) => {
    try {
      // All earlier sessions are revoked; the response carries a fresh TokenDto for this device.
      const tokens = await changePassword({ currentPassword, newPassword });
      const user = tokens?.accessToken ? acceptTokens(tokens) : null;
      toast.success('Password changed. Other devices have been logged out.');
      reset();
      onDone?.(user);
    } catch (err) {
      const fields = getFieldErrors(err);
      Object.entries(fields).forEach(([f, m]) => setError(f, { message: m }));
      if (err.response?.data?.code === 'AUTH_INVALID_CREDENTIALS') setError('currentPassword', { message: 'Current password is wrong' });
      else toast.error(getErrorMessage(err));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      <PasswordInput label="Current password" autoComplete="current-password" error={errors.currentPassword} {...register('currentPassword')} />
      <PasswordInput label="New password" autoComplete="new-password" hint={PASSWORD_HINT} error={errors.newPassword} {...register('newPassword')} />
      <PasswordInput label="Confirm new password" autoComplete="new-password" error={errors.confirmPassword} {...register('confirmPassword')} />
      <Button type="submit" loading={isSubmitting}>
        {submitLabel}
      </Button>
    </form>
  );
}
