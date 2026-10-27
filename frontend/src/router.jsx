import { lazy } from 'react';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import RoleRoute from './auth/RoleRoute';
import PermissionGate from './auth/PermissionGate';
import AdminLayout from './components/layout/AdminLayout';
import PartnerLayout from './components/layout/PartnerLayout';
import RootRedirect from './pages/RootRedirect';
import RouteError from './pages/RouteError';
import { ForbiddenPage, NotFoundPage, ServerErrorPage } from './pages/ErrorPage';
import LoginPage from './features/auth/pages/LoginPage';

const VerifyOtpPage = lazy(() => import('./features/auth/pages/VerifyOtpPage'));
const ForgotPasswordPage = lazy(() => import('./features/auth/pages/ForgotPasswordPage'));
const ChangePasswordPage = lazy(() => import('./features/auth/pages/ChangePasswordPage'));
const ProfilePage = lazy(() => import('./features/common/ProfilePage'));
const NotificationsPage = lazy(() => import('./features/common/NotificationsPage'));

// Admin
const AdminDashboardPage = lazy(() => import('./features/admin/dashboard/pages/AdminDashboardPage'));
const PartnersListPage = lazy(() => import('./features/admin/partners/pages/PartnersListPage'));
const PartnerFormPage = lazy(() => import('./features/admin/partners/pages/PartnerFormPage'));
const PartnerDetailPage = lazy(() => import('./features/admin/partners/pages/PartnerDetailPage'));
const TopupRequestsPage = lazy(() => import('./features/admin/topups/pages/TopupRequestsPage'));
const PaymentsPage = lazy(() => import('./features/admin/payments/pages/PaymentsPage'));
const PaymentDetailPage = lazy(() => import('./features/admin/payments/pages/PaymentDetailPage'));
const InvoicesPage = lazy(() => import('./features/admin/payments/pages/InvoicesPage'));
const CreditPacksPage = lazy(() => import('./features/admin/pricing/pages/CreditPacksPage'));
const RatePlansPage = lazy(() => import('./features/admin/pricing/pages/RatePlansPage'));
const RatePlanBuilderPage = lazy(() => import('./features/admin/pricing/pages/RatePlanBuilderPage'));
const MetaSettingsPage = lazy(() => import('./features/admin/whatsapp/pages/MetaSettingsPage'));
const WabaListPage = lazy(() => import('./features/admin/whatsapp/pages/WabaListPage'));
const WabaDetailPage = lazy(() => import('./features/admin/whatsapp/pages/WabaDetailPage'));
const ManualConnectPage = lazy(() => import('./features/admin/whatsapp/pages/ManualConnectPage'));
const SendersListPage = lazy(() => import('./features/admin/whatsapp/pages/SendersListPage'));
const SenderDetailPage = lazy(() => import('./features/admin/whatsapp/pages/SenderDetailPage'));
const TemplatesListPage = lazy(() => import('./features/admin/templates/pages/TemplatesListPage'));
const TemplateReviewPage = lazy(() => import('./features/admin/templates/pages/TemplateReviewPage'));
const AdminTemplateBuilderPage = lazy(() => import('./features/admin/templates/pages/AdminTemplateBuilderPage'));
const MessageLogsPage = lazy(() => import('./features/admin/messages/pages/MessageLogsPage'));
const MessageDetailPage = lazy(() => import('./features/admin/messages/pages/MessageDetailPage'));
const CampaignsPage = lazy(() => import('./features/admin/campaigns/pages/CampaignsPage'));
const ReportsPage = lazy(() => import('./features/admin/reports/pages/ReportsPage'));
const SettingsPage = lazy(() => import('./features/admin/settings/pages/SettingsPage'));
const StaffPage = lazy(() => import('./features/admin/staff/pages/StaffPage'));
const RolesPage = lazy(() => import('./features/admin/staff/pages/RolesPage'));
const AuditLogPage = lazy(() => import('./features/admin/audit/pages/AuditLogPage'));

// Partner
const PartnerDashboardPage = lazy(() => import('./features/partner/dashboard/pages/PartnerDashboardPage'));
const WalletPage = lazy(() => import('./features/partner/wallet/pages/WalletPage'));
const AddMoneyPage = lazy(() => import('./features/partner/wallet/pages/AddMoneyPage'));
const PaymentResultPage = lazy(() => import('./features/partner/wallet/pages/PaymentResultPage'));
const MySendersPage = lazy(() => import('./features/partner/senders/pages/MySendersPage'));
const ConnectNumberPage = lazy(() => import('./features/partner/senders/pages/ConnectNumberPage'));
const MySenderDetailPage = lazy(() => import('./features/partner/senders/pages/MySenderDetailPage'));
const SendMessagePage = lazy(() => import('./features/partner/send/pages/SendMessagePage'));
const MyCampaignsPage = lazy(() => import('./features/partner/campaigns/pages/MyCampaignsPage'));
const CampaignWizardPage = lazy(() => import('./features/partner/campaigns/pages/CampaignWizardPage'));
const CampaignDetailPage = lazy(() => import('./features/partner/campaigns/pages/CampaignDetailPage'));
const ContactsPage = lazy(() => import('./features/partner/contacts/pages/ContactsPage'));
const ContactImportPage = lazy(() => import('./features/partner/contacts/pages/ContactImportPage'));
const ContactGroupsPage = lazy(() => import('./features/partner/contacts/pages/ContactGroupsPage'));
const OptOutsPage = lazy(() => import('./features/partner/contacts/pages/OptOutsPage'));
const MyTemplatesPage = lazy(() => import('./features/partner/templates/pages/MyTemplatesPage'));
const TemplateBuilderPage = lazy(() => import('./features/partner/templates/pages/TemplateBuilderPage'));
const TemplateLibraryPage = lazy(() => import('./features/partner/templates/pages/TemplateLibraryPage'));
const MyTemplateDetailPage = lazy(() => import('./features/partner/templates/pages/MyTemplateDetailPage'));
const InboxPage = lazy(() => import('./features/partner/inbox/pages/InboxPage'));
const ChatPage = lazy(() => import('./features/partner/inbox/pages/ChatPage'));
const MyReportsPage = lazy(() => import('./features/partner/reports/pages/MyReportsPage'));
const DeveloperPage = lazy(() => import('./features/partner/developer/pages/DeveloperPage'));
const SupportPage = lazy(() => import('./features/partner/support/pages/SupportPage'));
const MorePage = lazy(() => import('./features/partner/more/pages/MorePage'));
const AiAssistantPage = lazy(() => import('./features/partner/ai/pages/AiAssistantPage'));
const ScheduledPage = lazy(() => import('./features/partner/scheduled/pages/ScheduledPage'));

/** Wraps an admin page with its permission check (403 when missing). */
const perm = (permission, element) => (
  <PermissionGate permission={permission} redirect>
    {element}
  </PermissionGate>
);

export const routes = [
  { path: '/', element: <RootRedirect /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/login/verify', element: <VerifyOtpPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/403', element: <ForbiddenPage /> },
  { path: '/404', element: <NotFoundPage /> },
  { path: '/500', element: <ServerErrorPage /> },
  {
    element: <ProtectedRoute allowPasswordChange />,
    children: [{ path: '/change-password', element: <ChangePasswordPage /> }],
  },
  {
    element: <ProtectedRoute />,
    errorElement: <RouteError />,
    children: [
      {
        path: '/admin',
        element: (
          <RoleRoute role="ADMIN">
            <AdminLayout />
          </RoleRoute>
        ),
        errorElement: <RouteError />,
        children: [
          { index: true, element: <AdminDashboardPage /> },
          { path: 'partners', element: perm('PARTNER_VIEW', <PartnersListPage />) },
          { path: 'partners/new', element: perm('PARTNER_MANAGE', <PartnerFormPage />) },
          { path: 'partners/:id', element: perm('PARTNER_VIEW', <PartnerDetailPage />) },
          { path: 'partners/:id/edit', element: perm('PARTNER_MANAGE', <PartnerFormPage />) },
          { path: 'topups', element: perm('TOPUP_VIEW', <TopupRequestsPage />) },
          { path: 'payments', element: perm('PAYMENT_VIEW', <PaymentsPage />) },
          { path: 'payments/:uid', element: perm('PAYMENT_VIEW', <PaymentDetailPage />) },
          { path: 'invoices', element: perm('PAYMENT_VIEW', <InvoicesPage />) },
          { path: 'pricing', element: <Navigate to="/admin/pricing/rate-plans" replace /> },
          { path: 'pricing/credit-packs', element: perm('PRICING_MANAGE', <CreditPacksPage />) },
          { path: 'pricing/rate-plans', element: perm('PRICING_MANAGE', <RatePlansPage />) },
          { path: 'pricing/rate-plans/:id', element: perm('PRICING_MANAGE', <RatePlanBuilderPage />) },
          { path: 'whatsapp', element: <Navigate to="/admin/whatsapp/senders" replace /> },
          { path: 'whatsapp/settings', element: perm('WHATSAPP_VIEW', <MetaSettingsPage />) },
          { path: 'whatsapp/accounts', element: perm('WHATSAPP_VIEW', <WabaListPage />) },
          { path: 'whatsapp/accounts/:id', element: perm('WHATSAPP_VIEW', <WabaDetailPage />) },
          { path: 'whatsapp/connect', element: perm('WHATSAPP_MANAGE', <ManualConnectPage />) },
          { path: 'whatsapp/senders', element: perm('WHATSAPP_VIEW', <SendersListPage />) },
          { path: 'whatsapp/senders/:id', element: perm('WHATSAPP_VIEW', <SenderDetailPage />) },
          { path: 'templates', element: perm('TEMPLATE_VIEW', <TemplatesListPage />) },
          { path: 'templates/review', element: perm('TEMPLATE_REVIEW', <TemplateReviewPage />) },
          { path: 'templates/new', element: perm('TEMPLATE_REVIEW', <AdminTemplateBuilderPage />) },
          { path: 'messages', element: perm('MESSAGE_LOG_VIEW', <MessageLogsPage />) },
          { path: 'messages/:uid', element: perm('MESSAGE_LOG_VIEW', <MessageDetailPage />) },
          { path: 'campaigns', element: perm('CAMPAIGN_VIEW', <CampaignsPage />) },
          { path: 'reports', element: perm('REPORT_VIEW', <ReportsPage />) },
          { path: 'settings', element: perm('SETTINGS_MANAGE', <SettingsPage />) },
          { path: 'staff', element: perm('STAFF_MANAGE', <StaffPage />) },
          { path: 'staff/roles', element: perm('STAFF_MANAGE', <RolesPage />) },
          { path: 'audit-logs', element: perm('AUDIT_VIEW', <AuditLogPage />) },
          { path: 'notifications', element: <NotificationsPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
      {
        path: '/partner',
        element: (
          <RoleRoute role="PARTNER">
            <PartnerLayout />
          </RoleRoute>
        ),
        errorElement: <RouteError />,
        children: [
          { index: true, element: <PartnerDashboardPage /> },
          { path: 'wallet', element: <WalletPage /> },
          { path: 'wallet/add', element: <AddMoneyPage /> },
          { path: 'wallet/payment/:uid', element: <PaymentResultPage /> },
          { path: 'senders', element: <MySendersPage /> },
          { path: 'senders/connect', element: <ConnectNumberPage /> },
          { path: 'senders/:id', element: <MySenderDetailPage /> },
          { path: 'send', element: <SendMessagePage /> },
          { path: 'campaigns', element: <MyCampaignsPage /> },
          { path: 'campaigns/new', element: <CampaignWizardPage /> },
          { path: 'campaigns/:id', element: <CampaignDetailPage /> },
          { path: 'contacts', element: <ContactsPage /> },
          { path: 'contacts/import', element: <ContactImportPage /> },
          { path: 'contacts/groups', element: <ContactGroupsPage /> },
          { path: 'contacts/opt-outs', element: <OptOutsPage /> },
          { path: 'templates', element: <MyTemplatesPage /> },
          { path: 'templates/new', element: <TemplateBuilderPage /> },
          { path: 'templates/library', element: <TemplateLibraryPage /> },
          { path: 'templates/:id', element: <MyTemplateDetailPage /> },
          { path: 'templates/:id/edit', element: <TemplateBuilderPage /> },
          { path: 'inbox', element: <InboxPage /> },
          { path: 'inbox/:senderId/:waId', element: <ChatPage /> },
          { path: 'reports', element: <MyReportsPage /> },
          { path: 'developer', element: <DeveloperPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'notifications', element: <NotificationsPage /> },
          { path: 'support', element: <SupportPage /> },
          { path: 'more', element: <MorePage /> },
          { path: 'ai', element: <AiAssistantPage /> },
          { path: 'scheduled', element: <ScheduledPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];

export const router = createBrowserRouter(routes);
