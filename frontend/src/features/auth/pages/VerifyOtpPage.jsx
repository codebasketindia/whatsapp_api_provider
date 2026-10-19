import { useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from '../../../auth/useAuth';
import { homePathFor } from '../../../auth/AuthProvider';
import { resendOtp } from '../../../api/endpoints/auth';
import { getErrorMessage } from '../../../api/axios';
import { session } from '../../../lib/storage';
import { Alert, Button } from '../../../components/ui';
import AuthShell from '../components/AuthShell';
import { TWO_FA_KEY, safeNext } from './LoginPage';

/** Six separate digit boxes; paste of a full code fills all of them. */
export function OtpBoxes({ value, onChange, length = 6, disabled, autoFocus = true }) {
  const refs = useRef([]);
  const digits = value.padEnd(length, ' ').slice(0, length).split('');
  const setAt = (i, ch) => {
    const arr = value.padEnd(length, ' ').slice(0, length).split('');
    arr[i] = ch;
    onChange(arr.join('').replace(/\s+$/g, '').replace(/\s/g, ''));
  };
  return (
    <div className="flex justify-center gap-2" role="group" aria-label="One-time code">
      {digits.map((d, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          value={d.trim()}
          disabled={disabled}
          autoFocus={autoFocus && i === 0}
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          aria-label={`Digit ${i + 1}`}
          maxLength={1}
          onChange={(e) => {
            const ch = e.target.value.replace(/\D/g, '').slice(-1);
            if (!ch) return;
            setAt(i, ch);
            refs.current[i + 1]?.focus();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace') {
              e.preventDefault();
              if (d.trim()) setAt(i, ' ');
              else if (i > 0) {
                setAt(i - 1, ' ');
                refs.current[i - 1]?.focus();
              }
            } else if (e.key === 'ArrowLeft') refs.current[i - 1]?.focus();
            else if (e.key === 'ArrowRight') refs.current[i + 1]?.focus();
          }}
          onPaste={(e) => {
            const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
            if (text) {
              e.preventDefault();
              onChange(text);
              refs.current[Math.min(text.length, length - 1)]?.focus();
            }
          }}
          className="h-14 w-11 rounded-lg border border-line bg-surface text-center text-xl font-semibold text-ink focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 sm:w-12"
        />
      ))}
    </div>
  );
}

export function useCountdown(seconds) {
  const [left, setLeft] = useState(seconds);
  useEffect(() => {
    if (left <= 0) return undefined;
    const t = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [left]);
  return [left, setLeft];
}

export default function VerifyOtpPage() {
  const { verifyTwoFactor, status, user } = useAuth();
  const navigate = useNavigate();
  const pending = useMemo(() => {
    try {
      return JSON.parse(session.get(TWO_FA_KEY) || 'null');
    } catch {
      return null;
    }
  }, []);
  const [otp, setOtp] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [left, setLeft] = useCountdown(30);

  if (status === 'authenticated' && user) return <Navigate to={homePathFor(user)} replace />;
  if (!pending?.challengeId) return <Navigate to="/login" replace />;

  const submit = async (code = otp) => {
    if (code.length !== 6) {
      setError('Enter the 6-digit code.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const u = await verifyTwoFactor(pending.challengeId, code);
      session.remove(TWO_FA_KEY);
      const next = safeNext(pending.next);
      navigate(u.mustChangePassword ? '/change-password' : next && next.startsWith('/admin') ? next : homePathFor(u), { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
      setOtp('');
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setResending(true);
    try {
      const res = await resendOtp(pending.challengeId);
      setLeft(res?.resendAfterSeconds || 30);
      toast.success('A new code has been sent');
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setResending(false);
    }
  };

  return (
    <AuthShell
      title="Enter verification code"
      subtitle={`We sent a 6-digit code to ${pending.maskedEmail || 'your email'}.`}
      footer={
        <button
          type="button"
          onClick={() => {
            session.remove(TWO_FA_KEY);
            navigate('/login');
          }}
          className="min-h-[36px] font-medium text-brand-700 hover:underline dark:text-brand-300"
        >
          Use a different account
        </button>
      }
    >
      {error && (
        <Alert variant="error" className="mb-4">
          {error}
        </Alert>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="space-y-5"
      >
        <OtpBoxes
          value={otp}
          disabled={busy}
          onChange={(v) => {
            setOtp(v);
            if (v.length === 6) submit(v);
          }}
        />
        <Button type="submit" fullWidth size="lg" loading={busy} leftIcon={ShieldCheck}>
          Verify and log in
        </Button>
        <p className="text-center text-sm text-muted">
          Didn&apos;t get it?{' '}
          {left > 0 ? (
            <span>Resend in {left}s</span>
          ) : (
            <button type="button" onClick={resend} disabled={resending} className="min-h-[36px] font-medium text-brand-700 hover:underline disabled:opacity-50 dark:text-brand-300">
              Resend code
            </button>
          )}
        </p>
      </form>
    </AuthShell>
  );
}
