import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { mobileSchema } from '../../../../lib/mobile';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import * as staffApi from '../../../../api/endpoints/staff';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { PASSWORD_HINT, PASSWORD_RULE } from '../../../../lib/constants';
import { Button, Drawer, Input, PasswordInput, PhoneInput, Select } from '../../../../components/ui';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter the name').max(120),
  email: z.string().trim().email('Enter a valid email'),
  mobile: mobileSchema,
  roleId: z.string().min(1, 'Choose a role'),
  password: z
    .string()
    .optional()
    .refine((v) => !v || PASSWORD_RULE.test(v), PASSWORD_HINT),
});

/** Add (staff=null) or edit a staff member. */
export default function StaffFormDrawer({ open, onClose, staff }) {
  const qc = useQueryClient();
  const editing = Boolean(staff);
  const rolesQ = useQuery({ queryKey: ['admin', 'roles'], queryFn: staffApi.listRoles, enabled: open });
  const adminRoles = (rolesQ.data || []).filter((r) => r.userType !== 'PARTNER' && r.code !== 'API_PARTNER');

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), defaultValues: { name: '', email: '', mobile: '', roleId: '', password: '' } });

  // UserDto carries role {code, name}; resolve its id from the roles list (id used when present).
  const staffRoleId = staff?.role?.id ?? (rolesQ.data || []).find((r) => r.code === staff?.role?.code)?.id;

  useEffect(() => {
    if (open) {
      reset({
        name: staff?.name || '',
        email: staff?.email || '',
        mobile: staff?.mobile || '',
        roleId: staffRoleId ? String(staffRoleId) : '',
        password: '',
      });
    }
  }, [open, staff, staffRoleId, reset]);

  const save = useMutation({
    mutationFn: (v) => {
      const body = { name: v.name.trim(), mobile: v.mobile, roleId: Number(v.roleId) };
      if (editing) return staffApi.updateStaff(staff.uid, body);
      return staffApi.createStaff({ ...body, email: v.email.trim().toLowerCase(), password: v.password || undefined });
    },
    onSuccess: () => {
      toast.success(editing ? 'Staff member updated' : 'Staff member added. Login details have been emailed.');
      qc.invalidateQueries({ queryKey: ['admin', 'staff'] });
      qc.invalidateQueries({ queryKey: ['admin', 'roles'] });
      onClose();
    },
    onError: (err) => {
      const fields = getFieldErrors(err);
      Object.entries(fields).forEach(([f, m]) => setError(f, { message: m }));
      if (err.response?.data?.code === 'CONFLICT') setError('email', { message: 'This email is already used' });
      toast.error(getErrorMessage(err));
    },
  });

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={editing ? 'Edit staff member' : 'Add staff member'}
      description={editing ? staff.email : 'Staff can log in to the admin panel with the permissions of their role.'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSubmit((v) => save.mutate(v))} loading={save.isPending}>
            {editing ? 'Save changes' : 'Add staff member'}
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <Input label="Full name" required error={errors.name} {...register('name')} />
        <Input label="Email" type="email" required disabled={editing} error={errors.email} {...register('email')} />
        <PhoneInput label="Mobile number (used to log in)" required value={watch('mobile')} onChange={(v) => setValue('mobile', v, { shouldValidate: true })} error={errors.mobile} />
        <Select
          label="Role"
          required
          placeholder={rolesQ.isLoading ? 'Loading roles…' : 'Choose a role'}
          options={adminRoles.map((r) => ({ value: String(r.id), label: r.name }))}
          error={errors.roleId}
          hint="Create roles with specific permissions under Roles & permissions."
          {...register('roleId')}
        />
        {!editing && (
          <PasswordInput
            label="Temporary password (optional)"
            autoComplete="new-password"
            hint={`Leave empty to email a temporary password. ${PASSWORD_HINT}`}
            error={errors.password}
            {...register('password')}
          />
        )}
        <button type="submit" className="hidden" aria-hidden />
      </form>
    </Drawer>
  );
}
