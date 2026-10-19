import { Hammer } from 'lucide-react';
import { EmptyState, PageHeader } from '../../components/ui';

/** Temporary placeholder used only by page files that are still being built. */
export default function ComingSoon({ title, subtitle }) {
  return (
    <>
      <PageHeader title={title} subtitle={subtitle} />
      <div className="card">
        <EmptyState icon={Hammer} title="Coming in this release" text="This screen is being finished and will be available shortly." />
      </div>
    </>
  );
}
