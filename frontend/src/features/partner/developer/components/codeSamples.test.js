import { describe, expect, it } from 'vitest';
import { EXAMPLES, LANGUAGES, codeSample } from './codeSamples';

describe('API code samples', () => {
  it('produces a snippet with auth headers for every language and example', () => {
    EXAMPLES.forEach((ex) => {
      LANGUAGES.forEach((lang) => {
        const code = codeSample(lang, ex, 'https://wa.example.com/api/v1/public');
        expect(code).toContain('https://wa.example.com/api/v1/public');
        expect(code).toMatch(/X-API-KEY/);
      });
    });
  });
});
