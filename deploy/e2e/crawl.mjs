import { chromium } from 'playwright';
const BASE = 'http://localhost:4173';
const [email, pass, prefix, width] = process.argv.slice(2);
const routes = {
  admin: ['', '/partners', '/partners/new', '/partners/1', '/partners/1?tab=wallet', '/partners/1?tab=senders', '/partners/1?tab=usage', '/partners/1?tab=api', '/partners/1?tab=activity', '/partners/1/edit',
    '/topups', '/payments', '/invoices', '/pricing/credit-packs', '/pricing/rate-plans', '/pricing/rate-plans/1',
    '/whatsapp/settings', '/whatsapp/accounts', '/whatsapp/accounts/1', '/whatsapp/connect', '/whatsapp/senders', '/whatsapp/senders/1',
    '/whatsapp/senders/1?tab=profile', '/whatsapp/senders/1?tab=verification', '/whatsapp/senders/1?tab=health', '/whatsapp/senders/1?tab=analytics',
    '/whatsapp/senders/1?tab=qr', '/whatsapp/senders/1?tab=welcome', '/whatsapp/senders/1?tab=blocked',
    '/templates', '/templates/review', '/templates/new', '/messages', '/messages/01JMSG0000000000000000000A', '/campaigns', '/reports',
    '/reports?tab=categories', '/reports?tab=countries', '/reports?tab=revenue', '/reports?tab=reconciliation',
    '/settings', '/settings?tab=payment', '/settings?tab=gst', '/settings?tab=smtp', '/settings?tab=charging', '/settings?tab=wallet', '/settings?tab=limits', '/settings?tab=security', '/settings?tab=retention',
    '/staff', '/staff/roles', '/audit-logs', '/notifications', '/profile'],
  partner: ['', '/wallet', '/wallet?tab=requests', '/wallet?tab=payments', '/wallet?tab=invoices', '/wallet/add', '/senders', '/senders/1', '/senders/1?tab=profile',
    '/senders/1?tab=health', '/senders/1?tab=qr', '/senders/1?tab=welcome', '/senders/1?tab=blocked', '/send', '/campaigns', '/campaigns/new',
    '/contacts', '/contacts/import', '/contacts/groups', '/contacts/opt-outs', '/templates', '/templates/new', '/templates/library', '/templates/1',
    '/inbox', '/inbox/1/919812345678', '/reports', '/reports?tab=usage', '/developer', '/developer?tab=webhook', '/developer?tab=logs', '/developer?tab=docs',
    '/profile', '/notifications', '/support', '/more'],
}[prefix];
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: Number(width), height: 860 } });
let current = '';
const problems = [];
p.on('response', r => { if (r.status() >= 400 && r.url().includes('/api/')) problems.push(`${current}: ${r.status()} ${r.request().method()} ${r.url().replace(BASE, '')}`); });
p.on('pageerror', e => problems.push(`${current}: PAGEERROR ${e.message}`));
await p.goto(BASE + '/login');
await p.getByLabel('Mobile number').fill(email);
await p.getByRole('textbox', { name: 'Password', exact: true }).fill(pass);
await p.getByRole('button', { name: 'Log in' }).click(); await p.waitForTimeout(2000);
for (const r of routes) {
  current = `/${prefix}${r}`;
  await p.goto(BASE + current); await p.waitForTimeout(1800);
  const scroll = await p.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
  if (scroll) problems.push(`${current}: HORIZONTAL SCROLL`);
  const errUi = await p.getByText(/Something went wrong|Could not load/i).count();
  if (errUi) problems.push(`${current}: ERROR STATE SHOWN`);
}
console.log(problems.length ? problems.join('\n') : 'NO PROBLEMS', `\n(${routes.length} routes @${width}px)`);
await b.close();
