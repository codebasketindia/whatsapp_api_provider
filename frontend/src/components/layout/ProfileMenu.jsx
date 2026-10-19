import { LogOut, UserCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import Avatar from '../ui/Avatar';
import DropdownMenu from '../ui/DropdownMenu';

export default function ProfileMenu({ profilePath }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <DropdownMenu
      trigger={({ toggle, open }) => (
        <button
          type="button"
          onClick={toggle}
          aria-expanded={open}
          aria-haspopup="menu"
          className="flex min-h-tap items-center gap-2 rounded-lg px-1.5 hover:bg-surface-2"
          aria-label="Account menu"
        >
          <Avatar name={user?.name} size="sm" />
          <span className="hidden max-w-[140px] truncate text-sm font-medium text-ink lg:block">{user?.name}</span>
        </button>
      )}
      items={[
        { label: 'My profile', icon: UserCircle, to: profilePath },
        { divider: true },
        {
          label: 'Log out',
          icon: LogOut,
          danger: true,
          onClick: async () => {
            await logout();
            navigate('/login', { replace: true });
          },
        },
      ]}
    >
      <div className="border-b border-line px-4 py-3">
        <p className="truncate text-sm font-semibold text-ink">{user?.name}</p>
        <p className="truncate text-xs text-muted">{user?.email}</p>
        {user?.partner?.businessName && <p className="mt-0.5 truncate text-xs text-muted">{user.partner.businessName}</p>}
        {user?.role?.name && user?.userType === 'ADMIN' && <p className="mt-0.5 truncate text-xs text-muted">{user.role.name}</p>}
      </div>
    </DropdownMenu>
  );
}
