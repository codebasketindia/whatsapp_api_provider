import { describe, expect, it } from 'vitest';
import { packCredit } from './packMath';

describe('credit pack math', () => {
  it('percent bonus', () => expect(packCredit({ price: '1000.0000', bonusType: 'PERCENT', bonusValue: '10' })).toEqual({ price: 1000, bonus: 100, credit: 1100 }));
  it('flat bonus', () => expect(packCredit({ price: 5000, bonusType: 'FLAT', bonusValue: '250.50' })).toEqual({ price: 5000, bonus: 250.5, credit: 5250.5 }));
});
