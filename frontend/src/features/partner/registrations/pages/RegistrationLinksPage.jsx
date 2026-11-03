import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Plus, Ticket } from 'lucide-react';
import * as regApi from '../../../../api/endpoints/registrations';
import { Badge, Button, Card, CopyButton, EmptyState, PageHeader, QueryState } from '../../../../components/ui';
import { formatDate, formatNumber } from '../../../../lib/format';

const STATUS = { ACTIVE: ['green', 'Open'], PAUSED: ['yellow', 'Paused'], CLOSED: ['gray', 'Closed'] };

/** RF1: the partner's shareable registration links. */
export default function RegistrationLinksPage() {
  const q = useQuery({ queryKey: ['partner', 'registration-forms'], queryFn: regApi.listForms });
  return (
    <>
      <PageHeader
        title="Registrations & free passes"
        subtitle="Share a link. People register with name and WhatsApp number and get their personal pass on WhatsApp."
        actions={<Button leftIcon={Plus} to="/partner/registrations/new">New link</Button>}
      />
      <QueryState query={q}>
        {(forms) =>
          !forms.length ? (
            <Card>
              <EmptyState icon={Ticket} title="No registration links yet" text="Create one, share it on WhatsApp, Facebook or a poster, and watch registrations come in."
                          action={<Button leftIcon={Plus} to="/partner/registrations/new">Create registration link</Button>} />
            </Card>
          ) : (
            <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {forms.map((f) => {
                const [tone, label] = STATUS[f.status] || ['gray', f.status];
                return (
                  <li key={f.id} className="card flex flex-col gap-3 p-4">
                    <div className="flex items-start justify-between gap-2">
                      <Link to={`/partner/registrations/${f.id}`} className="min-w-0 font-semibold text-ink hover:underline">{f.title}</Link>
                      <Badge tone={tone} dot>{label}</Badge>
                    </div>
                    <p className="text-3xl font-bold text-ink">{formatNumber(f.registrationsCount)}<span className="ml-1 text-sm font-normal text-muted">registered{f.maxRegistrations ? ` of ${formatNumber(f.maxRegistrations)}` : ''}</span></p>
                    <div className="flex min-w-0 items-center gap-2 rounded-lg bg-surface-2 px-3 py-2 text-sm">
                      <span className="min-w-0 flex-1 truncate">{f.publicUrl}</span>
                      <CopyButton value={f.publicUrl} toastMessage="Link copied" />
                    </div>
                    <p className="text-xs text-muted">Sends from {f.sender?.displayPhoneNumber || '—'} · created {formatDate(f.createdAt)}</p>
                  </li>
                );
              })}
            </ul>
          )
        }
      </QueryState>
    </>
  );
}
