import { describe, expect, it } from 'vitest';
import { bookingToForm, formToBooking, formToPackage, packageToForm, seatsText } from './travelHelpers';

describe('travel helpers', () => {
  it('maps packages both ways', () => {
    const f = packageToForm({ code: 'UMR', name: 'Umrah', price: '85000.00', seatsTotal: 10 });
    expect(f.price).toBe('85000.00');
    const body = formToPackage({ ...f, destination: ' ', duration: '15 days' }, { fileUid: 'F1' });
    expect(body).toMatchObject({ code: 'UMR', price: 85000, seatsTotal: 10, destination: null, duration: '15 days', fileUid: 'F1' });
  });

  it('maps bookings', () => {
    const b = formToBooking({ ...bookingToForm({}), packageId: '3', phone: '919812345678', travellers: '2', amount: '' });
    expect(b).toMatchObject({ packageId: 3, travellers: 2, amount: null, status: 'ENQUIRY' });
  });

  it('describes seats', () => {
    expect(seatsText({ seatsTotal: null })).toBe('No seat limit');
    expect(seatsText({ seatsTotal: 10, seatsAvailable: 0 })).toBe('Sold out');
    expect(seatsText({ seatsTotal: 10, seatsAvailable: 7 })).toBe('7 of 10 seats left');
  });
});
