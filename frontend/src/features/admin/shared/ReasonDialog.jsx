import { useEffect, useState } from 'react';
import { ConfirmDialog, Textarea } from '../../../components/ui';

/**
 * Confirm dialog that asks for a reason (required by default, min 3 characters).
 * onConfirm(reason)
 */
export default function ReasonDialog({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirm',
  label = 'Reason',
  placeholder,
  danger = false,
  loading = false,
  required = true,
  minLength = 3,
  maxLength = 500,
  children,
}) {
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (!open) setReason('');
  }, [open]);
  const tooShort = required && reason.trim().length < minLength;
  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      title={title}
      message={message}
      confirmLabel={confirmLabel}
      danger={danger}
      loading={loading}
      confirmDisabled={tooShort}
      onConfirm={() => onConfirm(reason.trim())}
    >
      {children}
      <Textarea
        label={label}
        required={required}
        rows={3}
        maxLength={maxLength}
        showCount
        value={reason}
        placeholder={placeholder}
        onChange={(e) => setReason(e.target.value)}
        hint={required ? `At least ${minLength} characters.` : undefined}
      />
    </ConfirmDialog>
  );
}
