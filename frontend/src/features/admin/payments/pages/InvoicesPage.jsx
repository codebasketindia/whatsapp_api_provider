import { useQuery } from '@tanstack/react-query';
import { Download, ReceiptIndianRupee } from 'lucide-react';
import toast from 'react-hot-toast';
import * as paymentsApi from '../../../../api/endpoints/payments';
import { getErrorMessage } from '../../../../api/axios';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatDate } from '../../../../lib/format';
import { Button, DataTable, DateRangePicker, EmptyState, FilterBar, Input, Money, PageHeader } from '../../../../components/ui';

export default function InvoicesPage() {
  const [state, update] = useUrlState({ page: 0, partnerId: '', from: '', to: '' });
  const q = useQuery({
    queryKey: ['admin', 'invoices', state],
    queryFn: () => paymentsApi.listInvoices({ ...state, size: 20 }),
    placeholderData: (p) => p,
  });
  const download = (inv) => paymentsApi.downloadInvoice(inv.id, inv.invoiceNo).catch((e) => toast.error(getErrorMessage(e)));
  const columns = [
    {
      key: 'invoiceNo',
      header: 'Invoice',
      primary: true,
      render: (i) => (
        <span>
          <span className="block font-medium text-ink">{i.invoiceNo}</span>
          <span className="block text-xs text-muted">{formatDate(i.invoiceDate)}</span>
        </span>
      ),
    },
    { key: 'partner', header: 'Billed to', render: (i) => <span><span className="block">{i.partner?.businessName}</span><span className="block text-xs text-muted">{i.partner?.gstin || 'No GSTIN'}</span></span> },
    { key: 'taxable', header: 'Taxable', align: 'right', hideOnMobile: true, render: (i) => <Money value={i.taxableAmount} /> },
    {
      key: 'tax',
      header: 'Tax',
      align: 'right',
      hideOnMobile: true,
      render: (i) => (Number(i.igst) > 0 ? <span>IGST <Money value={i.igst} /></span> : <span>CGST+SGST <Money value={Number(i.cgst) + Number(i.sgst)} /></span>),
    },
    { key: 'total', header: 'Total', align: 'right', render: (i) => <Money value={i.total} className="font-semibold" /> },
    {
      key: 'pdf',
      header: <span className="sr-only">Download</span>,
      align: 'right',
      hideOnMobile: true,
      render: (i) => <Button size="sm" variant="secondary" leftIcon={Download} onClick={(e) => { e.stopPropagation(); download(i); }}>PDF</Button>,
    },
  ];
  return (
    <>
      <PageHeader title="Invoices" subtitle="GST invoices generated for wallet top-ups." backTo="/admin/payments" backLabel="Payments" />
      <FilterBar>
        <Input aria-label="Partner id" placeholder="Partner id" inputMode="numeric" value={state.partnerId} onChange={(e) => update({ partnerId: e.target.value.replace(/\D/g, '') })} />
      </FilterBar>
      <DateRangePicker className="mb-4" value={{ from: state.from, to: state.to }} onChange={(r) => update(r)} allowClear />
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        mobileActions={(i) => <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => download(i)}>Download PDF</Button>}
        empty={<EmptyState icon={ReceiptIndianRupee} title="No invoices" text="Invoices are created automatically when GST is enabled in Settings." />}
      />
    </>
  );
}
