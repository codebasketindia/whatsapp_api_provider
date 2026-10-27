import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { BookOpen, Pencil, Plus, Trash2, X } from 'lucide-react';
import toast from 'react-hot-toast';
import * as aiApi from '../../../../api/endpoints/ai';
import { getErrorMessage } from '../../../../api/axios';
import {
  Button, Card, ConfirmDialog, Drawer, EmptyState, ErrorState, FileUpload, IconButton, Input, SearchInput, Skeleton, Switch, Textarea,
} from '../../../../components/ui';
import { formatRelative } from '../../../../lib/format';
import KnowledgeMedia, { MediaBadge } from './KnowledgeMedia';
import { KNOWLEDGE_MAX, knowledgeSnippet } from './aiHelpers';

const EXAMPLES = ['Price list', 'Shop address & timing', 'Return policy', 'Product catalogue photo'];
const ACCEPT = 'image/jpeg,image/png,video/mp4,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document';

const schema = z.object({
  title: z.string().trim().min(1, 'Give it a short title').max(120, 'Up to 120 characters'),
  content: z.string().trim().min(1, 'Write the information').max(KNOWLEDGE_MAX, `Up to ${KNOWLEDGE_MAX} characters`),
  active: z.boolean(),
  sortOrder: z.coerce.number({ invalid_type_error: 'Enter a number' }).int().min(0).max(9999),
});

function KnowledgeDrawer({ open, item, preset, onClose }) {
  const qc = useQueryClient();
  const { control, register, handleSubmit, reset, watch, formState } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { title: '', content: '', active: true, sortOrder: 0 },
  });
  const [media, setMedia] = useState(null); // { fileUid, url, type, filename } | null
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(0);

  useEffect(() => {
    if (!open) return;
    reset({ title: item?.title || preset || '', content: item?.content || '', active: item?.active ?? true, sortOrder: item?.sortOrder ?? 0 });
    setMedia(item?.media || null);
    setFile(null);
  }, [open, item, preset, reset]);

  const upload = async (f) => {
    setFile(f);
    if (!f) return;
    if (f.type.startsWith('image/') && f.size > 5 * 1024 * 1024) {
      toast.error('Photos can be up to 5 MB');
      setFile(null);
      return;
    }
    setUploading(1);
    try {
      const res = await aiApi.uploadKnowledgeMedia(f, (e) => setUploading(Math.max(1, Math.round((e.loaded / (e.total || 1)) * 100))));
      setMedia(res);
    } catch (e) {
      toast.error(getErrorMessage(e));
      setFile(null);
    } finally {
      setUploading(0);
    }
  };

  const save = useMutation({
    mutationFn: (v) => {
      const body = { ...v, title: v.title.trim(), content: v.content.trim(), fileUid: media?.fileUid ?? null };
      return item ? aiApi.updateKnowledge(item.id, body) : aiApi.createKnowledge(body);
    },
    onSuccess: () => {
      toast.success(item ? 'Information updated' : 'Information added');
      qc.invalidateQueries({ queryKey: ['partner', 'ai', 'knowledge'] });
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const content = watch('content');
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={item ? 'Edit information' : 'Add information'}
      description="Write it the way you would explain it to a new staff member. The AI uses only this."
      width="md:w-[520px]"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={save.isPending} disabled={uploading > 0} onClick={handleSubmit((v) => save.mutate(v))}>Save</Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input label="Title" required maxLength={120} placeholder="e.g. Price list" error={formState.errors.title?.message} {...register('title')} />
        <Textarea
          label="Information"
          required
          rows={8}
          maxLength={KNOWLEDGE_MAX}
          showCount
          value={content}
          placeholder={'e.g.\nAC service: ₹499\nAC gas refill: ₹2,499\nVisit charge waived on orders above ₹999'}
          error={formState.errors.content?.message}
          {...register('content')}
        />
        <div>
          <span className="label-base">Photo, video or document (optional)</span>
          <p className="mb-2 text-xs text-muted">The AI sends it to the customer when this information is used. Photo up to 5 MB, video/PDF/Word up to 16 MB.</p>
          {media ? (
            <div className="relative inline-block max-w-full rounded-lg border border-line p-2">
              <KnowledgeMedia media={media} />
              <IconButton icon={X} label="Remove file" size="sm" variant="secondary" className="absolute right-1 top-1" onClick={() => { setMedia(null); setFile(null); }} />
            </div>
          ) : (
            <>
              <FileUpload value={file} onChange={upload} accept={ACCEPT} maxSizeMB={16} disabled={uploading > 0} />
              {uploading > 0 && <p className="mt-1 text-xs text-muted">Uploading… {uploading}%</p>}
            </>
          )}
        </div>
        <Controller name="active" control={control} render={({ field }) => <Switch checked={field.value} onChange={field.onChange} label="Use this information" description="Off = the AI ignores it." />} />
        <Input type="number" inputMode="numeric" min={0} label="Order" hint="Lower numbers come first." error={formState.errors.sortOrder?.message} {...register('sortOrder')} />
      </div>
    </Drawer>
  );
}

export default function KnowledgeTab() {
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [drawer, setDrawer] = useState(null); // { item?, preset? }
  const [deleting, setDeleting] = useState(null);
  const q = useQuery({ queryKey: ['partner', 'ai', 'knowledge', search], queryFn: () => aiApi.listKnowledge({ search }), placeholderData: (p) => p });
  const items = q.data || [];

  const toggle = useMutation({
    mutationFn: (it) => aiApi.updateKnowledge(it.id, { title: it.title, content: it.content, active: !it.active, sortOrder: it.sortOrder, fileUid: it.media?.fileUid ?? null }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['partner', 'ai', 'knowledge'] }),
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const del = useMutation({
    mutationFn: (it) => aiApi.deleteKnowledge(it.id),
    onSuccess: () => {
      toast.success('Deleted');
      setDeleting(null);
      qc.invalidateQueries({ queryKey: ['partner', 'ai', 'knowledge'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  return (
    <>
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput className="flex-1" value={search} onSearch={setSearch} placeholder="Search your information" />
        <Button leftIcon={Plus} onClick={() => setDrawer({})}>Add information</Button>
      </div>
      {q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : q.isLoading ? (
        <div className="space-y-3">{[1, 2, 3].map((i) => <Skeleton key={i} className="h-20 w-full" />)}</div>
      ) : !items.length ? (
        search ? (
          <EmptyState compact title="Nothing matches your search" />
        ) : (
          <Card>
            <EmptyState
              icon={BookOpen}
              title="Teach the AI about your business"
              text="Add what customers usually ask. The AI answers only from this. Start with one of these:"
              action={
                <div className="flex flex-wrap justify-center gap-2">
                  {EXAMPLES.map((e) => <Button key={e} size="sm" variant="secondary" leftIcon={Plus} onClick={() => setDrawer({ preset: e })}>{e}</Button>)}
                </div>
              }
            />
          </Card>
        )
      ) : (
        <ul className="space-y-3">
          {items.map((it) => (
            <li key={it.id} className="card flex items-start gap-3 p-4">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-semibold text-ink">{it.title}</p>
                  <MediaBadge media={it.media} />
                </div>
                <p className="mt-1 line-clamp-2 text-sm text-muted">{knowledgeSnippet(it.content)}</p>
                <p className="mt-1 text-xs text-muted">Updated {formatRelative(it.updatedAt)}</p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center">
                <Switch checked={it.active} onChange={() => toggle.mutate(it)} disabled={toggle.isPending} className="min-h-0" />
                <div className="flex">
                  <IconButton icon={Pencil} label={`Edit ${it.title}`} onClick={() => setDrawer({ item: it })} />
                  <IconButton icon={Trash2} label={`Delete ${it.title}`} onClick={() => setDeleting(it)} />
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      <KnowledgeDrawer open={Boolean(drawer)} item={drawer?.item} preset={drawer?.preset} onClose={() => setDrawer(null)} />
      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        onConfirm={() => del.mutate(deleting)}
        loading={del.isPending}
        danger
        title="Delete this information?"
        message={<>The AI will no longer use <strong>{deleting?.title}</strong>.</>}
        confirmLabel="Delete"
      />
    </>
  );
}
