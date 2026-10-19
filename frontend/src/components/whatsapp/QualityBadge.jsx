import { Badge } from '../ui';
import InfoTip from '../ui/InfoTip';

const MAP = {
  GREEN: { tone: 'green', label: 'High quality' },
  YELLOW: { tone: 'amber', label: 'Medium quality' },
  RED: { tone: 'red', label: 'Low quality' },
  UNKNOWN: { tone: 'gray', label: 'Quality not rated' },
};

/** Meta quality rating as a coloured badge with explanation. */
export default function QualityBadge({ rating, withInfo = false }) {
  const m = MAP[String(rating || 'UNKNOWN').toUpperCase()] || MAP.UNKNOWN;
  return (
    <span className="inline-flex items-center gap-1">
      <Badge tone={m.tone} dot>
        {m.label}
      </Badge>
      {withInfo && <InfoTip term="QUALITY_RATING" />}
    </span>
  );
}
