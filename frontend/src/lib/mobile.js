import { z } from 'zod';

/** Indian mobile in API format: "91" + 10 digits starting 6-9 (what PhoneInput produces). */
export const MOBILE_RE = /^91[6-9]\d{9}$/;

/** Required mobile number field (login id for every user). */
export const mobileSchema = z
  .string({ required_error: 'Enter the mobile number' })
  .min(1, 'Enter the mobile number')
  .refine((v) => MOBILE_RE.test(v), 'Enter a valid 10-digit mobile number');
