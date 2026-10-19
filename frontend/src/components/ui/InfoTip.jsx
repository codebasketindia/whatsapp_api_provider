import { HelpCircle } from 'lucide-react';
import Tooltip from './Tooltip';
import { glossary } from '../../lib/metaGlossary';

/** Small "?" icon with an explanation. Pass `text`, or `term` to use the Meta glossary. */
export default function InfoTip({ text, term, side }) {
  const content = text || glossary(term);
  if (!content) return null;
  return (
    <Tooltip content={content} side={side}>
      <button type="button" className="inline-flex h-6 w-6 items-center justify-center rounded-full text-muted hover:text-ink" aria-label="More information">
        <HelpCircle className="h-4 w-4" aria-hidden />
      </button>
    </Tooltip>
  );
}
