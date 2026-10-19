import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as templatesApi from '../../../../api/endpoints/templates';
import TemplateBuilder from '../../../../components/whatsapp/TemplateBuilder';
import { Button, Card, EmptyState, PageHeader, PageLoader } from '../../../../components/ui';
import PartnerSelect from '../../shared/PartnerSelect';
import { useApiMutation } from '../../shared/useApiMutation';
import { useAdminWabaOptions } from '../components/useAdminWabaOptions';

/** TP16: platform template created by admin (optionally on behalf of a partner). Submitted to Meta directly. */
export default function AdminTemplateBuilderPage() {
  const navigate = useNavigate();
  const [partner, setPartner] = useState(null);
  const wabas = useAdminWabaOptions('PLATFORM');
  const save = useApiMutation({
    mutationFn: ({ body, submit }) => templatesApi.createPlatformTemplate({ ...body, submit, partnerId: partner?.id ?? null }),
    success: (t) => (t?.status === 'PENDING' ? 'Submitted to Meta' : 'Template saved'),
    invalidate: [['admin', 'templates']],
    onSuccess: () => navigate('/admin/templates'),
  });
  if (wabas.isLoading) return <PageLoader />;
  if (!wabas.options.length) {
    return (
      <>
        <PageHeader title="New template" backTo="/admin/templates" backLabel="Templates" />
        <div className="card"><EmptyState title="Add our WhatsApp account first" text="Platform templates belong to one of our WhatsApp Business Accounts." action={<Button to="/admin/whatsapp/accounts">WhatsApp accounts</Button>} /></div>
      </>
    );
  }
  return (
    <>
      <PageHeader title="New platform template" subtitle="Created on our WhatsApp account and sent to Meta for approval." backTo="/admin/templates" backLabel="Templates" />
      <Card className="mb-4" title="For a partner (optional)" subtitle="Link the template to a partner so it appears in their template list.">
        <PartnerSelect label="Partner" value={partner} onChange={setPartner} />
      </Card>
      <TemplateBuilder
        wabaOptions={wabas.options}
        uploadHeader={templatesApi.uploadTemplateHeaderAdmin}
        onSubmit={(body, opts) => save.mutate({ body, ...opts })}
        submitting={save.isPending}
        allowDraft
        businessName={partner?.businessName}
      />
    </>
  );
}
