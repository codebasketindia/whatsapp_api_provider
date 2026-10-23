import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { mobileSchema } from '../../lib/mobile';
import toast from 'react-hot-toast';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from '../../auth/useAuth';
import { useTheme } from '../../hooks/useTheme';
import * as meApi from '../../api/endpoints/me';
import { getErrorMessage } from '../../api/axios';
import { Badge, Button, Card, ConfirmDialog, Input, KeyValue, PageHeader, PasswordInput, PhoneInput, RadioCards, Switch } from '../../components/ui';
import ChangePasswordForm from '../auth/components/ChangePasswordForm';
import { formatDateTime, humanize } from '../../lib/format';

const profileSchema = z.object({
  name: z.string().trim().min(2, 'Enter your name').max(120),
  mobile: mobileSchema,
});

function ProfileForm() {
  const { user, setUser } = useAuth();
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting, isDirty },
    reset,
  } = useForm({ resolver: zodResolver(profileSchema), defaultValues: { name: user?.name || '', mobile: user?.mobile || '' } });
  const onSubmit = async (values) => {
    try {
      const updated = await meApi.updateMe({ name: values.name.trim(), mobile: values.mobile, theme: user?.theme });
      setUser(updated);
      reset({ name: updated.name, mobile: updated.mobile || '' });
      toast.success('Profile saved');
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };
  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      <Input label="Full name" autoComplete="name" error={errors.name} {...register('name')} />
      <Input label="Email" value={user?.email || ''} disabled hint="Contact support to change your login email." readOnly />
      <PhoneInput label="Mobile number (used to log in)" required value={watch('mobile')} onChange={(v) => setValue('mobile', v, { shouldDirty: true, shouldValidate: true })} error={errors.mobile} />
      <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
        Save changes
      </Button>
    </form>
  );
}

function ThemeCard() {
  const { theme, setTheme } = useTheme();
  const { user, setUser } = useAuth();
  const change = async (t) => {
    setTheme(t);
    try {
      const updated = await meApi.updateMe({ name: user.name, mobile: user.mobile || null, theme: t });
      setUser(updated);
    } catch {
      /* theme preference is still applied locally */
    }
  };
  return (
    <Card title="Appearance">
      <RadioCards
        name="theme"
        value={theme}
        onChange={change}
        columns={3}
        options={[
          { value: 'LIGHT', label: 'Light' },
          { value: 'DARK', label: 'Dark' },
          { value: 'SYSTEM', label: 'Same as device' },
        ]}
      />
    </Card>
  );
}

function TwoFactorCard() {
  const { user, setUser } = useAuth();
  const [target, setTarget] = useState(null);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const confirm = async () => {
    setBusy(true);
    try {
      const updated = await meApi.setTwoFactor({ enabled: target, password });
      setUser(updated);
      toast.success(target ? 'Two-step login turned on' : 'Two-step login turned off');
      setTarget(null);
      setPassword('');
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Card title="Two-step login" subtitle="Ask for a 6-digit code sent to your email every time you log in.">
      <Switch
        checked={Boolean(user?.twoFactorEnabled)}
        onChange={(v) => setTarget(v)}
        label={user?.twoFactorEnabled ? 'On' : 'Off'}
        description="Recommended for all admin accounts."
      />
      <ConfirmDialog
        open={target !== null}
        onClose={() => {
          setTarget(null);
          setPassword('');
        }}
        onConfirm={confirm}
        loading={busy}
        confirmDisabled={!password}
        danger={target === false}
        title={target ? 'Turn on two-step login?' : 'Turn off two-step login?'}
        message="Enter your password to confirm."
        confirmLabel={target ? 'Turn on' : 'Turn off'}
      >
        <PasswordInput label="Password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      </ConfirmDialog>
    </Card>
  );
}

export default function ProfilePage() {
  const { user, isAdmin } = useAuth();
  return (
    <>
      <PageHeader title="My profile" subtitle="Your personal details, password and security." />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Personal details">
          <ProfileForm />
        </Card>
        <div className="space-y-5">
          <Card title="Account">
            <KeyValue
              items={[
                { label: 'Account type', value: isAdmin ? 'Admin' : 'API partner' },
                { label: 'Role', value: user?.role?.name || humanize(user?.role?.code), hidden: !isAdmin },
                {
                  label: 'Access',
                  value: user?.isSuperAdmin ? <Badge tone="brand">Full access</Badge> : `${user?.permissions?.length || 0} permissions`,
                  hidden: !isAdmin,
                },
                { label: 'Business', value: user?.partner?.businessName, hidden: isAdmin },
                { label: 'Partner code', value: user?.partner?.partnerCode, hidden: isAdmin },
                { label: 'Status', value: user?.partner?.status ? humanize(user.partner.status) : undefined, hidden: isAdmin },
                { label: 'Last login', value: user?.lastLoginAt ? formatDateTime(user.lastLoginAt) : undefined, hidden: !user?.lastLoginAt },
              ]}
            />
          </Card>
          <ThemeCard />
        </div>
        <Card title="Change password" subtitle="Changing your password logs you out on other devices.">
          <ChangePasswordForm />
        </Card>
        {isAdmin && (
          <div className="space-y-5">
            <TwoFactorCard />
            <Card>
              <p className="flex items-start gap-2 text-sm text-muted">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />
                Never share your password or login codes. Our team will never ask for them.
              </p>
            </Card>
          </div>
        )}
      </div>
    </>
  );
}
