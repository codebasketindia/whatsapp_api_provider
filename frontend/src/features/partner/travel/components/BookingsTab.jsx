import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import toast from 'react-hot-toast';
import * as travelApi from '../../../../api/endpoints/travel';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { Button, ConfirmDialog, DataTable, Drawer, EmptyState, Input, PhoneInput, SearchInput, Select, StatusBadge, Textarea } from '../../../../components/ui';
import { formatMoney, formatPhone } from '../../../../lib/format';
import { BOOKING_STATUS, bookingToForm, formToBooking } from './travelHelpers';

function BookingDrawer({ open, item, packages, onClose }) {
  const qc = useQueryClient();
  const [v, setV] = useState(bookingToForm());
  const [errors, setErrors] = useState({});
  const [deleting, setDeleting] = useState(false);
  useEffect(() => {
    if (open) {
      setV(bookingToForm(item || {}));
      setErrors({});
    }
  }, [open, item]);
  const set = (k) => (e) => setV((s) => ({ ...s, [k]: e.target.value }));
  const done = (msg) => {
    toast.success(msg);
    qc.invalidateQueries({ queryKey: ['partner', 'travel'] });
    onClose();
  };
  const save = useMutation({
    mutationFn: () => (item ? travelApi.updateBooking(item.uid, formToBooking(v)) : travelApi.createBooking(formToBooking(v))),
    onSuccess: () => done(item ? 'Booking saved' : 'Booking added'),
    onError: (e) => {
      setErrors(getFieldErrors(e));
      toast.error(getErrorMessage(e));
    },
  });
  const del = useMutation({ mutationFn: () => travelApi.deleteBooking(item.uid), onSuccess: () => done('Booking deleted'), onError: (e) => toast.error(getErrorMessage(e)) });
  return (
    <Drawer open={open} onClose={onClose} width="md:w-[520px]" title={item ? 'Edit booking' : 'Add booking'}
            description="The AI tells a customer only about their own bookings (matched by WhatsApp number)."
            footer={<>
              {item && <Button variant="danger" className="mr-auto" onClick={() => setDeleting(true)}>Delete</Button>}
              <Button variant="secondary" onClick={onClose}>Cancel</Button>
              <Button loading={save.isPending} disabled={!v.packageId || !v.phone} onClick={() => save.mutate()}>Save</Button>
            </>}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Select className="sm:col-span-2" label="Package" required value={v.packageId} onChange={set('packageId')} error={errors.packageId}
                options={packages.map((p) => ({ value: p.id, label: `${p.name} (${p.code})` }))} placeholder="Choose a package" />
        <PhoneInput label="Customer WhatsApp number" required value={v.phone} onChange={(p) => setV((s) => ({ ...s, phone: p }))} error={errors.phone} />
        <Input label="Customer name" maxLength={120} value={v.customerName} onChange={set('customerName')} />
        <Input type="number" min={1} label="Travellers" value={v.travellers} onChange={set('travellers')} error={errors.travellers} />
        <Select label="Status" value={v.status} onChange={set('status')} options={BOOKING_STATUS} />
        <Input type="number" min={0} label="Total amount (₹)" value={v.amount} onChange={set('amount')} />
        <Input type="number" min={0} label="Paid (₹)" value={v.paidAmount} onChange={set('paidAmount')} />
        <Input type="date" label="Travel date" value={v.travelDate} onChange={set('travelDate')} />
        <Textarea className="sm:col-span-2" label="Notes" rows={3} maxLength={1000} value={v.notes} onChange={set('notes')} />
      </div>
      <ConfirmDialog open={deleting} onClose={() => setDeleting(false)} onConfirm={() => del.mutate()} loading={del.isPending} danger title="Delete this booking?" confirmLabel="Delete" />
    </Drawer>
  );
}

export default function BookingsTab() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [packageId, setPackageId] = useState('');
  const [page, setPage] = useState(0);
  const [drawer, setDrawer] = useState(null);
  const packages = useQuery({ queryKey: ['partner', 'travel', 'packages'], queryFn: () => travelApi.listPackages() });
  const q = useQuery({
    queryKey: ['partner', 'travel', 'bookings', search, status, packageId, page],
    queryFn: () => travelApi.listBookings({ search, status, packageId, page, size: 20 }),
    placeholderData: (p) => p,
  });
  const pk = packages.data || [];
  return (
    <>
      <div className="mb-3 flex flex-col gap-3 md:flex-row">
        <SearchInput className="flex-1" value={search} onSearch={(s) => { setSearch(s); setPage(0); }} placeholder="Search name or mobile" />
        <Select className="md:w-56" value={packageId} onChange={(e) => { setPackageId(e.target.value); setPage(0); }} options={pk.map((p) => ({ value: p.id, label: p.name }))} placeholder="All packages" />
        <Select className="md:w-44" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} options={BOOKING_STATUS} placeholder="All statuses" />
        <Button leftIcon={Plus} disabled={!pk.length} onClick={() => setDrawer({})}>Add booking</Button>
      </div>
      <DataTable
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={setPage}
        rowKey={(b) => b.uid}
        onRowClick={(b) => setDrawer({ item: b })}
        empty={<EmptyState compact title={pk.length ? 'No bookings yet' : 'Add a package first'} />}
        columns={[
          { key: 'customerName', header: 'Customer', primary: true, render: (b) => b.customerName || formatPhone(b.phone) },
          { key: 'phone', header: 'WhatsApp', render: (b) => formatPhone(b.phone) },
          { key: 'package', header: 'Package', render: (b) => b.travelPackage?.name || '—' },
          { key: 'travellers', header: 'Travellers', align: 'right' },
          { key: 'amount', header: 'Amount', align: 'right', hideOnMobile: true, render: (b) => (b.amount != null ? formatMoney(b.amount) : '—') },
          { key: 'status', header: 'Status', render: (b) => <StatusBadge status={b.status} /> },
        ]}
      />
      <BookingDrawer open={Boolean(drawer)} item={drawer?.item} packages={pk} onClose={() => setDrawer(null)} />
    </>
  );
}
