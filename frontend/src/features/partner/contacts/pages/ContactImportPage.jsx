import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Download, FileSpreadsheet } from 'lucide-react';
import toast from 'react-hot-toast';
import * as contactsApi from '../../../../api/endpoints/contacts';
import { downloadStoredFile } from '../../../../api/endpoints/files';
import { getErrorMessage } from '../../../../api/axios';
import {
  Alert, Button, Card, Checkbox, FileUpload, Input, PageHeader, Select, StatCard, Stepper,
} from '../../../../components/ui';
import { formatNumber } from '../../../../lib/format';
import ContactsSubnav from '../components/ContactsSubnav';
import { buildContactImportMapping, guessColumn, toAttributeKey } from '../../common/columnMapping';

const COUNTRIES = [
  { value: 'IN', label: 'India (+91)' },
  { value: 'AE', label: 'UAE (+971)' },
  { value: 'US', label: 'USA (+1)' },
  { value: 'GB', label: 'UK (+44)' },
  { value: 'SA', label: 'Saudi Arabia (+966)' },
  { value: 'SG', label: 'Singapore (+65)' },
];

export default function ContactImportPage() {
  const qc = useQueryClient();
  const [step, setStep] = useState(0);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [map, setMap] = useState({ phone: '', name: '', email: '', tags: '' });
  const [extra, setExtra] = useState([]);
  const [groupId, setGroupId] = useState('');
  const [defaultCountry, setDefaultCountry] = useState('IN');
  const [updateExisting, setUpdateExisting] = useState(true);
  const [result, setResult] = useState(null);
  const groups = useQuery({ queryKey: ['partner', 'contact-groups', 'all'], queryFn: () => contactsApi.listContactGroups({ size: 100 }) });

  const previewM = useMutation({
    mutationFn: () => contactsApi.previewContactImport(file),
    onSuccess: (p) => {
      setPreview(p);
      const cols = p.columns || [];
      const m = { phone: guessColumn(cols, 'phone'), name: guessColumn(cols, 'name'), email: guessColumn(cols, 'email'), tags: guessColumn(cols, 'tags') };
      setMap(m);
      setExtra(cols.filter((c) => !Object.values(m).includes(c)).map((c) => ({ column: c, key: toAttributeKey(c), include: true })));
      setStep(1);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const importM = useMutation({
    mutationFn: () =>
      contactsApi.importContacts({
        file,
        mapping: buildContactImportMapping({ ...map, extraColumns: extra.filter((x) => !Object.values(map).includes(x.column)) }),
        groupId: groupId || undefined,
        defaultCountry,
        updateExisting,
      }),
    onSuccess: (r) => {
      setResult(r);
      setStep(2);
      qc.invalidateQueries({ queryKey: ['partner', 'contacts'] });
      qc.invalidateQueries({ queryKey: ['partner', 'contact-groups'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const columns = preview?.columns || [];
  const colOptions = useMemo(() => columns.map((c) => ({ value: c, label: c })), [columns]);
  const sample = (col) => {
    const idx = columns.indexOf(col);
    return idx < 0 ? '' : (preview?.sampleRows || []).slice(0, 2).map((r) => r[idx]).filter(Boolean).join(', ');
  };

  return (
    <>
      <PageHeader title="Import contacts" subtitle="Upload a CSV or Excel file with your customers' numbers" />
      <ContactsSubnav />
      <Stepper className="mb-6" current={step} steps={[{ key: 'file', label: 'Choose file' }, { key: 'map', label: 'Match columns' }, { key: 'done', label: 'Result' }]} />
      <div className="mx-auto max-w-3xl">
        {step === 0 && (
          <Card title="Choose a file">
            <FileUpload accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" maxSizeMB={10} value={file} onChange={setFile} hint="CSV or Excel (.xlsx) · up to 50,000 rows · max 10 MB" />
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
              <Button variant="link" leftIcon={Download} onClick={() => contactsApi.downloadContactImportSample().catch((e) => toast.error(getErrorMessage(e)))}>Download sample file</Button>
              <Button onClick={() => previewM.mutate()} loading={previewM.isPending} disabled={!file}>Continue</Button>
            </div>
          </Card>
        )}
        {step === 1 && preview && (
          <div className="space-y-5">
            <Alert variant="info">We found {formatNumber(preview.rowCount)} rows and {columns.length} columns.</Alert>
            <Card title="Match your columns">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {[
                  ['phone', 'WhatsApp number', true],
                  ['name', 'Name', false],
                  ['email', 'Email', false],
                  ['tags', 'Tags (comma separated)', false],
                ].map(([k, label, req]) => (
                  <Select
                    key={k}
                    label={label}
                    required={req}
                    value={map[k]}
                    onChange={(e) => setMap({ ...map, [k]: e.target.value })}
                    options={colOptions}
                    placeholder={req ? 'Choose column' : 'Not in file'}
                    hint={map[k] ? `e.g. ${sample(map[k])}` : undefined}
                    error={req && !map[k] ? 'Required' : undefined}
                  />
                ))}
                <Select label="Numbers without country code are from" value={defaultCountry} onChange={(e) => setDefaultCountry(e.target.value)} options={COUNTRIES} />
                <Select label="Add everyone to a group (optional)" value={groupId} onChange={(e) => setGroupId(e.target.value)} options={(groups.data?.content || []).map((g) => ({ value: g.id, label: g.name }))} placeholder="No group" />
              </div>
              <Checkbox className="mt-4" label="Update contacts that already exist" description="Otherwise existing numbers are skipped" checked={updateExisting} onChange={(e) => setUpdateExisting(e.target.checked)} />
            </Card>
            {extra.filter((x) => !Object.values(map).includes(x.column)).length > 0 && (
              <Card title="Other columns" subtitle="Save them as custom fields to use in campaigns">
                <div className="space-y-3">
                  {extra
                    .filter((x) => !Object.values(map).includes(x.column))
                    .map((x) => (
                      <div key={x.column} className="grid items-end gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                        <Checkbox
                          label={x.column}
                          description={sample(x.column) ? `e.g. ${sample(x.column)}` : undefined}
                          checked={x.include}
                          onChange={(e) => setExtra(extra.map((y) => (y.column === x.column ? { ...y, include: e.target.checked } : y)))}
                        />
                        <Input
                          aria-label={`Field name for ${x.column}`}
                          value={x.key}
                          disabled={!x.include}
                          onChange={(e) => setExtra(extra.map((y) => (y.column === x.column ? { ...y, key: toAttributeKey(e.target.value) } : y)))}
                          prefix="#"
                        />
                      </div>
                    ))}
                </div>
              </Card>
            )}
            <div className="flex justify-between">
              <Button variant="secondary" onClick={() => setStep(0)}>Back</Button>
              <Button onClick={() => importM.mutate()} loading={importM.isPending} disabled={!map.phone}>Import {formatNumber(preview.rowCount)} rows</Button>
            </div>
          </div>
        )}
        {step === 2 && result && (
          <Card>
            <div className="mb-5 flex items-center gap-3">
              <CheckCircle2 className="h-10 w-10 text-green-600" aria-hidden />
              <div>
                <h2 className="text-lg font-semibold text-ink">Import finished</h2>
                <p className="text-sm text-muted">{formatNumber(result.total)} rows checked</p>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <StatCard label="Added" tone="green" value={formatNumber(result.created)} />
              <StatCard label="Updated" tone="blue" value={formatNumber(result.updated)} />
              <StatCard label="Invalid" tone="red" value={formatNumber(result.invalid)} />
              <StatCard label="Duplicates" tone="amber" value={formatNumber(result.duplicates)} />
            </div>
            <div className="mt-5 flex flex-wrap gap-2">
              {result.reportFileUid && (
                <Button variant="secondary" leftIcon={FileSpreadsheet} onClick={() => downloadStoredFile(result.reportFileUid, 'import-report.csv').catch((e) => toast.error(getErrorMessage(e)))}>
                  Download row report
                </Button>
              )}
              <Button to="/partner/contacts">View contacts</Button>
              <Button variant="ghost" onClick={() => { setStep(0); setFile(null); setResult(null); setPreview(null); }}>Import another file</Button>
            </div>
          </Card>
        )}
      </div>
    </>
  );
}
