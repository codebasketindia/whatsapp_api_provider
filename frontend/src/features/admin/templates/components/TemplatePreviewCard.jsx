import { templateToPreview } from '../../../../components/whatsapp/TemplateBuilder';
import { WhatsAppPreview } from '../../../../components/ui';

/** WhatsApp-style preview of a TemplateDto. */
export default function TemplatePreviewCard({ template, businessName }) {
  const p = templateToPreview(template);
  return (
    <div className="wa-wallpaper rounded-xl p-3">
      <WhatsAppPreview header={p.header} body={p.body} footer={p.footer} buttons={p.buttons} businessName={businessName} />
    </div>
  );
}
