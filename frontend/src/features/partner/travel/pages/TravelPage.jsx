import { PageHeader, Tabs, useActiveTab } from '../../../../components/ui';
import PackagesTab from '../components/PackagesTab';
import BookingsTab from '../components/BookingsTab';

const TABS = [
  { value: 'packages', label: 'Packages' },
  { value: 'bookings', label: 'Bookings' },
];

/** TP/TB: travel packages and bookings — the database the AI assistant answers from. */
export default function TravelPage() {
  const tab = useActiveTab(TABS);
  return (
    <>
      <PageHeader title="Travel packages" subtitle="Tours, prices, dates and seats. The AI assistant answers customers only from this." />
      <Tabs tabs={TABS} urlParam="tab" className="mb-5" />
      {tab === 'packages' ? <PackagesTab /> : <BookingsTab />}
    </>
  );
}
