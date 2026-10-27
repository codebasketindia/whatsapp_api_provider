import { FileText, Film, Image } from 'lucide-react';
import { Badge } from '../../../../components/ui';
import { useProtectedUrl } from '../../inbox/components/ChatBubble';

const ICON = { image: Image, video: Film, document: FileText };
const LABEL = { image: 'Photo', video: 'Video', document: 'Document' };

export function MediaBadge({ media }) {
  if (!media) return null;
  const Icon = ICON[media.type] || FileText;
  return (
    <Badge tone="blue">
      <Icon className="h-3.5 w-3.5" aria-hidden /> {LABEL[media.type] || 'File'}
    </Badge>
  );
}

/** Preview card for a knowledge item's photo / video / document (as WhatsApp would show it). */
export default function KnowledgeMedia({ media, className }) {
  const url = useProtectedUrl(media?.url || null);
  if (!media) return null;
  if (media.type === 'image') {
    return url ? <img src={url} alt={media.filename || ''} className={`max-h-56 rounded-md object-contain ${className || ''}`} /> : <div className={`h-32 w-48 animate-pulse rounded-md bg-black/10 ${className || ''}`} />;
  }
  if (media.type === 'video') {
    return url ? <video src={url} controls className={`max-h-56 rounded-md ${className || ''}`} /> : <div className={`h-32 w-48 animate-pulse rounded-md bg-black/10 ${className || ''}`} />;
  }
  return (
    <a href={url || undefined} download={media.filename || 'document'} className={`flex items-center gap-2 rounded-md bg-black/5 p-2 dark:bg-white/10 ${className || ''}`}>
      <FileText className="h-6 w-6 shrink-0 text-red-500" aria-hidden />
      <span className="truncate text-sm">{media.filename || 'Document'}</span>
    </a>
  );
}
