import {
  BarChart3, Bell, Building2, ClipboardList, Code2, Contact, CreditCard, FileText, Home, Inbox, LayoutDashboard,
  Megaphone, MessageSquare, MessagesSquare, MoreHorizontal, Package, Phone, ReceiptIndianRupee, ScrollText, Send,
  Settings, ShieldCheck, Smartphone, Tags, Users, Wallet, Wrench, Bot, CalendarClock,
} from 'lucide-react';

/** Admin sidebar (filtered by permission). `end` = exact match. */
export const ADMIN_NAV = [
  {
    section: 'Overview',
    items: [{ to: '/admin', label: 'Dashboard', icon: LayoutDashboard, permission: 'DASHBOARD_VIEW', end: true }],
  },
  {
    section: 'Business',
    items: [
      { to: '/admin/partners', label: 'Partners', icon: Building2, permission: 'PARTNER_VIEW' },
      { to: '/admin/topups', label: 'Top-up requests', icon: Wallet, permission: 'TOPUP_VIEW' },
      { to: '/admin/payments', label: 'Payments', icon: CreditCard, permission: 'PAYMENT_VIEW', end: true },
      { to: '/admin/invoices', label: 'Invoices', icon: ReceiptIndianRupee, permission: 'PAYMENT_VIEW' },
      { to: '/admin/pricing/credit-packs', label: 'Credit packs', icon: Package, permission: 'PRICING_MANAGE' },
      { to: '/admin/pricing/rate-plans', label: 'Rate plans', icon: Tags, permission: 'PRICING_MANAGE' },
    ],
  },
  {
    section: 'WhatsApp',
    items: [
      { to: '/admin/whatsapp/senders', label: 'All senders', icon: Smartphone, permission: 'WHATSAPP_VIEW' },
      { to: '/admin/whatsapp/accounts', label: 'WhatsApp accounts', icon: Phone, permission: 'WHATSAPP_VIEW' },
      { to: '/admin/whatsapp/settings', label: 'Meta settings', icon: Wrench, permission: 'WHATSAPP_VIEW' },
      { to: '/admin/templates', label: 'Templates', icon: FileText, permission: 'TEMPLATE_VIEW', end: true },
      { to: '/admin/templates/review', label: 'Template review', icon: ClipboardList, permission: 'TEMPLATE_REVIEW' },
      { to: '/admin/messages', label: 'Message logs', icon: MessageSquare, permission: 'MESSAGE_LOG_VIEW' },
      { to: '/admin/campaigns', label: 'Campaigns', icon: Megaphone, permission: 'CAMPAIGN_VIEW' },
    ],
  },
  {
    section: 'Insights',
    items: [{ to: '/admin/reports', label: 'Reports', icon: BarChart3, permission: 'REPORT_VIEW' }],
  },
  {
    section: 'System',
    items: [
      { to: '/admin/settings', label: 'Settings', icon: Settings, permission: 'SETTINGS_MANAGE' },
      { to: '/admin/staff', label: 'Staff & roles', icon: Users, permission: 'STAFF_MANAGE' },
      { to: '/admin/audit-logs', label: 'Audit log', icon: ScrollText, permission: 'AUDIT_VIEW' },
      { to: '/admin/notifications', label: 'Notifications', icon: Bell },
    ],
  },
];

export const PARTNER_NAV = [
  {
    section: 'Main',
    items: [
      { to: '/partner', label: 'Home', icon: Home, end: true },
      { to: '/partner/send', label: 'Send message', icon: Send },
      { to: '/partner/inbox', label: 'Inbox', icon: Inbox },
      { to: '/partner/ai', label: 'AI Assistant', icon: Bot },
      { to: '/partner/scheduled', label: 'Scheduled', icon: CalendarClock },
      { to: '/partner/campaigns', label: 'Campaigns', icon: Megaphone },
      { to: '/partner/wallet', label: 'Wallet', icon: Wallet },
    ],
  },
  {
    section: 'WhatsApp',
    items: [
      { to: '/partner/senders', label: 'My numbers', icon: Smartphone },
      { to: '/partner/templates', label: 'Templates', icon: FileText },
      { to: '/partner/contacts', label: 'Contacts', icon: Contact },
      { to: '/partner/reports', label: 'Reports', icon: BarChart3 },
    ],
  },
  {
    section: 'Account',
    items: [
      { to: '/partner/developer', label: 'Developer API', icon: Code2 },
      { to: '/partner/notifications', label: 'Notifications', icon: Bell },
      { to: '/partner/support', label: 'Help & support', icon: ShieldCheck },
    ],
  },
];

/** Mobile bottom navigation for partners. */
export const PARTNER_BOTTOM_NAV = [
  { to: '/partner', label: 'Home', icon: Home, end: true },
  { to: '/partner/send', label: 'Send', icon: Send },
  { to: '/partner/inbox', label: 'Inbox', icon: MessagesSquare },
  { to: '/partner/wallet', label: 'Wallet', icon: Wallet },
  { to: '/partner/more', label: 'More', icon: MoreHorizontal },
];
