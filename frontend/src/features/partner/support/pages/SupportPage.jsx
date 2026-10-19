import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronDown, LifeBuoy, Mail, Phone } from 'lucide-react';
import * as authApi from '../../../../api/endpoints/auth';
import { Button, Card, PageHeader, Skeleton } from '../../../../components/ui';
import { cn } from '../../../../lib/cn';

const FAQ = [
  ['Why did my message fail?', 'Open Reports, tap the message and read the reason. Common reasons: the number is not on WhatsApp, the 24-hour window closed (use a template), or the customer opted out of marketing. Failed messages are refunded to your wallet.'],
  ['What is the 24-hour window?', 'When a customer messages you, you can reply with any message for 24 hours. After that, WhatsApp allows only approved templates until the customer writes again.'],
  ['How long does template approval take?', 'Usually a few minutes, sometimes up to 24 hours. Templates on numbers provided by us are first checked by our team.'],
  ['When is money taken from my wallet?', 'The price is held when you send and charged when the message is delivered. If it fails, the held amount comes back automatically.'],
  ['I paid by bank transfer. How do I add it?', 'Go to Wallet → Add money → I already paid, and send the UTR / reference number with a screenshot. We add it after checking.'],
  ['Why is my number paused?', 'We pause sending if WhatsApp lowers your number quality or restricts it, to protect the number. Check the number Health tab and contact us.'],
  ['How do I stop people who do not want offers?', 'Customers who reply STOP are opted out automatically. You can change the stop words in Contacts → Opt-outs.'],
  ['Can I use my own WhatsApp number?', 'Yes, if it is enabled for your account. Go to My numbers → Connect WhatsApp number and follow the Facebook steps.'],
];

function Item({ q, a }) {
  const [open, setOpen] = useState(false);
  return (
    <li>
      <button type="button" onClick={() => setOpen((v) => !v)} aria-expanded={open} className="flex min-h-[56px] w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm font-medium text-ink hover:bg-surface-2">
        {q}
        <ChevronDown className={cn('h-5 w-5 shrink-0 text-muted transition-transform', open && 'rotate-180')} aria-hidden />
      </button>
      {open && <p className="px-4 pb-4 text-sm text-muted">{a}</p>}
    </li>
  );
}

export default function SupportPage() {
  const b = useQuery({ queryKey: ['public', 'branding'], queryFn: authApi.getBranding, staleTime: 3600_000 });
  return (
    <>
      <PageHeader title="Help & support" subtitle="We are here to help" />
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[360px_minmax(0,1fr)]">
        <Card title="Contact us">
          {b.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : (
            <div className="space-y-3">
              <p className="flex items-center gap-2 text-sm text-ink"><LifeBuoy className="h-5 w-5 text-brand-600" /> {b.data?.companyName || 'Support team'}</p>
              {b.data?.supportEmail && <Button fullWidth variant="secondary" leftIcon={Mail} href={`mailto:${b.data.supportEmail}`}>{b.data.supportEmail}</Button>}
              {b.data?.supportPhone && <Button fullWidth variant="secondary" leftIcon={Phone} href={`tel:${b.data.supportPhone}`}>{b.data.supportPhone}</Button>}
              {!b.data?.supportEmail && !b.data?.supportPhone && <p className="text-sm text-muted">Contact details will be shared by your account manager.</p>}
              <p className="text-xs text-muted">Please mention your partner code and the message ID (from Reports) when you contact us.</p>
            </div>
          )}
        </Card>
        <Card title="Common questions" padded={false}>
          <ul className="divide-y divide-line">{FAQ.map(([q, a]) => <Item key={q} q={q} a={a} />)}</ul>
        </Card>
      </div>
    </>
  );
}
