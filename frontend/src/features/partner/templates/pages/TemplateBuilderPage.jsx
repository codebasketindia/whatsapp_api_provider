import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import { Alert, Button, EmptyState, ErrorState, PageHeader, PageLoader } from '../../../../components/ui';
import TemplateBuilder, { componentsToBuilder } from '../../../../components/whatsapp/TemplateBuilder';
import { useWabaOptions } from '../components/useWabaOptions';

const EDITABLE = ['DRAFT', 'APPROVED', 'REJECTED', 'PAUSED', 'INTERNAL_REJECTED', 'PENDING_REVIEW'];

export default function TemplateBuilderPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user } = useAuth();
  const wabas = useWabaOptions();
  const existing = useQuery({ queryKey: ['partner', 'templates', 'detail', id], queryFn: () => templatesApi.getMyTemplate(id), enabled: Boolean(id) });

  const save = useMutation({
    mutationFn: ({ body }) => (id ? templatesApi.updateMyTemplate(id, body) : templatesApi.createMyTemplate(body)),
    onSuccess: (t, { submit }) => {
      qc.invalidateQueries({ queryKey: ['partner', 'templates'] });
      const msg =
        t?.status === 'PENDING_REVIEW'
          ? 'Sent to our team for review. We will submit it to Meta.'
          : t?.status === 'PENDING'
            ? 'Submitted to Meta. Approval usually takes a few minutes.'
            : submit ? 'Template saved' : 'Draft saved';
      toast.success(msg);
      navigate(`/partner/templates/${t.id}`);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  if (wabas.isLoading || (id && existing.isLoading)) return <PageLoader />;
  if (existing.isError) return <ErrorState error={existing.error} onRetry={existing.refetch} />;
  if (!wabas.options.length) {
    return (
      <>
        <PageHeader title="New template" backTo="/partner/templates" backLabel="Templates" />
        <div className="card">
          <EmptyState title="You need a WhatsApp number first" text="Templates belong to a WhatsApp account. Connect a number or ask us to assign one." action={<Button to="/partner/senders">My numbers</Button>} />
        </div>
      </>
    );
  }
  const t = existing.data;
  if (t && !EDITABLE.includes(t.status)) {
    return (
      <>
        <PageHeader title={`Edit ${t.name}`} backTo={`/partner/templates/${t.id}`} backLabel="Template" />
        <Alert variant="info">This template cannot be changed while it is {t.status.toLowerCase().replace(/_/g, ' ')}. Please wait for the review to finish.</Alert>
      </>
    );
  }
  const initial = t ? { ...componentsToBuilder(t), wabaDbId: t.waba?.id } : undefined;
  return (
    <>
      <PageHeader
        title={t ? `Edit ${t.name}` : 'New template'}
        subtitle={t?.status === 'APPROVED' ? 'Editing an approved template sends it to Meta for review again.' : 'Fill in the details. The preview shows how customers will see it.'}
        backTo={t ? `/partner/templates/${t.id}` : '/partner/templates'}
        backLabel={t ? 'Template' : 'Templates'}
      />
      <TemplateBuilder
        initial={initial}
        wabaOptions={wabas.options}
        lockIdentity={Boolean(t)}
        uploadHeader={templatesApi.uploadTemplateHeader}
        onSubmit={(body, opts) => save.mutate({ body, ...opts })}
        submitting={save.isPending}
        allowDraft={!t || t.status === 'DRAFT'}
        businessName={user?.partner?.businessName}
      />
    </>
  );
}
