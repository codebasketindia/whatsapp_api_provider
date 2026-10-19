import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import toast from 'react-hot-toast';
import { forgotPassword, resetPassword } from '../../../api/endpoints/auth';
import { getErrorMessage } from '../../../api/axios';
import { PASSWORD_HINT, PASSWORD_RULE } from '../../../lib/constants';
import { Alert, Button, Input, PasswordInput } from '../../../components/ui';
import AuthShell from '../components/AuthShell';
import { OtpBoxes, useCountdown } from './VerifyOtpPage';

const emailSchema = z.object({ email: z.string().trim().min(1, 'Enter your email').email('Enter a valid email') });
const resetSchema = z
  .object({
    newPassword: z.string().regex(PASSWORD_RULE, PASSWORD_HINT),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { message: 'Passwords do not match', path: ['confirmPassword'] });

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [challengeId, setChallengeId] = useState(null);
  const [otp, setOtp] = useState('');
  const [error, setError] = useState(null);
  const [left, setLeft] = useCountdown(0);

  const emailForm = useForm({ resolver: zodResolver(emailSchema), defaultValues: { email: '' } });
  const resetForm = useForm({ resolver: zodResolver(resetSchema), defaultValues: { newPassword: '', confirmPassword: '' } });

  const sendCode = async ({ email: e }) => {
    setError(null);
    try {
      const res = await forgotPassword(e.trim().toLowerCase());
      setEmail(e.trim().toLowerCase());
      setChallengeId(res?.challengeId);
      setStep(2);
      setLeft(30);
      toast.success('If this email is registered, a code has been sent');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const doReset = async ({ newPassword }) => {
    if (otp.length !== 6) {
      setError('Enter the 6-digit code from your email.');
      return;
    }
    setError(null);
    try {
      await resetPassword({ challengeId, otp, newPassword });
      navigate('/login?reset=1', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <AuthShell
      title={step === 1 ? 'Forgot password' : 'Set a new password'}
      subtitle={step === 1 ? 'Enter your email and we will send you a 6-digit code.' : `Enter the code sent to ${email} and choose a new password.`}
      footer={
        <Link to="/login" className="inline-flex min-h-[36px] items-center font-medium text-brand-700 hover:underline dark:text-brand-300">
          Back to log in
        </Link>
      }
    >
      {error && (
        <Alert variant="error" className="mb-4">
          {error}
        </Alert>
      )}
      {step === 1 ? (
        <form onSubmit={emailForm.handleSubmit(sendCode)} className="space-y-4" noValidate>
          <Input label="Email" type="email" autoComplete="email" autoFocus error={emailForm.formState.errors.email} {...emailForm.register('email')} />
          <Button type="submit" fullWidth size="lg" loading={emailForm.formState.isSubmitting}>
            Send code
          </Button>
        </form>
      ) : (
        <form onSubmit={resetForm.handleSubmit(doReset)} className="space-y-4" noValidate>
          <div>
            <span className="label-base text-center">6-digit code</span>
            <OtpBoxes value={otp} onChange={setOtp} />
            <p className="mt-2 text-center text-sm text-muted">
              {left > 0 ? (
                `You can request a new code in ${left}s`
              ) : (
                <button type="button" onClick={() => sendCode({ email })} className="min-h-[36px] font-medium text-brand-700 hover:underline dark:text-brand-300">
                  Send a new code
                </button>
              )}
            </p>
          </div>
          <PasswordInput label="New password" autoComplete="new-password" hint={PASSWORD_HINT} error={resetForm.formState.errors.newPassword} {...resetForm.register('newPassword')} />
          <PasswordInput label="Confirm new password" autoComplete="new-password" error={resetForm.formState.errors.confirmPassword} {...resetForm.register('confirmPassword')} />
          <Button type="submit" fullWidth size="lg" loading={resetForm.formState.isSubmitting}>
            Change password
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
