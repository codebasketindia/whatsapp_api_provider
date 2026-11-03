// Form value ↔ API body for travel packages and bookings.

export const PACKAGE_STATUS = [
  { value: 'ACTIVE', label: 'Active (AI can offer it)' },
  { value: 'INACTIVE', label: 'Inactive (hidden from AI)' },
  { value: 'SOLD_OUT', label: 'Sold out' },
];

export const BOOKING_STATUS = [
  { value: 'ENQUIRY', label: 'Enquiry' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

const emptyToNull = (v) => (v === '' || v === undefined || v === null ? null : v);
const num = (v) => (v === '' || v === undefined || v === null ? null : Number(v));

export function packageToForm(p = {}) {
  return {
    code: p.code || '', name: p.name || '', destination: p.destination || '', duration: p.duration || '',
    price: p.price ?? '', priceNote: p.priceNote || '', startDate: p.startDate || '', endDate: p.endDate || '',
    seatsTotal: p.seatsTotal ?? '', inclusions: p.inclusions || '', exclusions: p.exclusions || '', itinerary: p.itinerary || '',
    details: p.details || '', status: p.status || 'ACTIVE', sortOrder: p.sortOrder ?? 0,
  };
}

export function formToPackage(v, media) {
  return {
    code: v.code.trim(), name: v.name.trim(), destination: emptyToNull(v.destination?.trim()), duration: emptyToNull(v.duration?.trim()),
    price: num(v.price), priceNote: emptyToNull(v.priceNote?.trim()), startDate: emptyToNull(v.startDate), endDate: emptyToNull(v.endDate),
    seatsTotal: num(v.seatsTotal), inclusions: emptyToNull(v.inclusions?.trim()), exclusions: emptyToNull(v.exclusions?.trim()),
    itinerary: emptyToNull(v.itinerary?.trim()), details: emptyToNull(v.details?.trim()), status: v.status, sortOrder: num(v.sortOrder) ?? 0,
    fileUid: media?.fileUid ?? null,
  };
}

export function bookingToForm(b = {}) {
  return {
    packageId: b.travelPackage?.id ?? '', phone: b.phone || '', customerName: b.customerName || '', travellers: b.travellers ?? 1,
    amount: b.amount ?? '', paidAmount: b.paidAmount ?? '', status: b.status || 'ENQUIRY', travelDate: b.travelDate || '', notes: b.notes || '',
  };
}

export function formToBooking(v) {
  return {
    packageId: Number(v.packageId), phone: v.phone, customerName: emptyToNull(v.customerName?.trim()), travellers: Number(v.travellers) || 1,
    amount: num(v.amount), paidAmount: num(v.paidAmount), status: v.status, travelDate: emptyToNull(v.travelDate), notes: emptyToNull(v.notes?.trim()),
  };
}

/** "7 of 10 seats left" / "Sold out" / "No seat limit". */
export function seatsText(p) {
  if (p.seatsTotal === null || p.seatsTotal === undefined) return 'No seat limit';
  if (p.seatsAvailable === 0) return 'Sold out';
  return `${p.seatsAvailable} of ${p.seatsTotal} seats left`;
}
