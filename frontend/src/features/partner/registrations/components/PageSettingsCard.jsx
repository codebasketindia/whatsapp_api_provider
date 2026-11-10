import { Card, Input } from '../../../../components/ui';

/** Booking page look: banner, heading, note, button text and the combo offer (all optional). */
export default function PageSettingsCard({ v, set, errors = {} }) {
  const f = (k, label, extra = {}) => (
    <Input label={label} value={v[k]} error={errors[k]} onChange={(e) => set({ [k]: e.target.value })} {...extra} />
  );
  return (
    <Card title="Booking page" subtitle="Banner, heading and combo offer shown on the public page. Leave blank for defaults.">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {f('heroImageUrl', 'Banner image URL', { maxLength: 500, placeholder: '/ziyarat/hajj-2027-banner.jpg or https://…', className: 'md:col-span-2' })}
        {f('pageHeading', 'Page heading', { maxLength: 160, placeholder: 'Apna Ziyarat Pass Chuniye' })}
        {f('ctaLabel', 'Button text', { maxLength: 60, placeholder: 'Register Karein' })}
        {f('pageNote', 'Note under the passes', { maxLength: 300, className: 'md:col-span-2' })}
        {f('comboTitle', 'Combo title', { maxLength: 160, placeholder: 'Teeno Paid Pass • 7 Bus Rounds' })}
        {f('comboSubtitle', 'Combo subtitle', { maxLength: 200, placeholder: 'Limited Seats • 15 December 2026 tak' })}
        {f('comboCodes', 'Combo package codes', { maxLength: 200, placeholder: 'SMM,AMM,ACZ', hint: 'Combo price applies when all these are selected.',
          onChange: (e) => set({ comboCodes: e.target.value.toUpperCase().replace(/[^A-Z0-9,]/g, '') }) })}
        {f('comboValidUntil', 'Combo valid until (IST)', { type: 'datetime-local' })}
        {f('comboMrp', 'Combo MRP (₹)', { type: 'number', min: 0 })}
        {f('comboPrice', 'Combo price (₹)', { type: 'number', min: 0 })}
        {f('invoiceName', 'Name on invoices', { maxLength: 160, placeholder: 'Your business name', hint: 'Blank = your business name from the profile.', className: 'md:col-span-2' })}
        {f('passSupportLine', 'Line at the bottom of Ziyarat-card passes', { maxLength: 120, placeholder: 'Emergency Support : +966590550490', className: 'md:col-span-2' })}
      </div>
    </Card>
  );
}
