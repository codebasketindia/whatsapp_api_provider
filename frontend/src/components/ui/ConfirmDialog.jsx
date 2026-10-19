import { useEffect, useState } from 'react';
import Modal from './Modal';
import Button from './Button';
import Input from './Input';

/**
 * Confirmation for money and destructive actions.
 * - confirmWord: user must type it (e.g. "DELETE") before the button enables.
 * - children: extra content (e.g. reason textarea) rendered between message and buttons.
 */
export default function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title = 'Are you sure?',
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  loading = false,
  confirmWord,
  confirmDisabled = false,
  children,
}) {
  const [typed, setTyped] = useState('');
  useEffect(() => {
    if (!open) setTyped('');
  }, [open]);
  const wordOk = !confirmWord || typed.trim().toUpperCase() === String(confirmWord).toUpperCase();
  return (
    <Modal
      open={open}
      onClose={loading ? undefined : onClose}
      dismissible={!loading}
      title={title}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading} disabled={!wordOk || confirmDisabled}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {message && <div className="text-sm text-ink">{message}</div>}
      {children && <div className="mt-4 space-y-4">{children}</div>}
      {confirmWord && (
        <Input
          className="mt-4"
          label={`Type ${confirmWord} to confirm`}
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
          autoComplete="off"
        />
      )}
    </Modal>
  );
}
