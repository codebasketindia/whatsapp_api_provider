import { NavLink } from 'react-router-dom';
import { cn } from '../../lib/cn';

/** Renders grouped nav sections; `collapsed` shows icons only (desktop). */
export default function SidebarNav({ sections, collapsed = false, onNavigate, badges = {} }) {
  return (
    <nav className="space-y-5" aria-label="Main">
      {sections.map((sec) => (
        <div key={sec.section}>
          {!collapsed && <p className="mb-1 px-3 text-xs font-semibold uppercase tracking-wider text-muted">{sec.section}</p>}
          <ul className="space-y-0.5">
            {sec.items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  onClick={onNavigate}
                  title={collapsed ? item.label : undefined}
                  className={({ isActive }) =>
                    cn(
                      'flex min-h-tap items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
                      collapsed && 'justify-center px-0',
                      isActive
                        ? 'bg-brand-50 text-brand-800 dark:bg-brand-500/15 dark:text-brand-200'
                        : 'text-ink/80 hover:bg-surface-2 hover:text-ink',
                    )
                  }
                >
                  <item.icon className="h-5 w-5 shrink-0" aria-hidden />
                  {!collapsed && <span className="flex-1 truncate">{item.label}</span>}
                  {!collapsed && badges[item.to] ? (
                    <span className="rounded-full bg-red-600 px-2 py-0.5 text-[11px] font-semibold text-white">{badges[item.to]}</span>
                  ) : null}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
