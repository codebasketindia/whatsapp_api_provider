import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CalendarClock, FileSpreadsheet, Send, Users, UserSquare2 } from 'lucide-react';
import toast from 'react-hot-toast';
import * as campaignsApi from '../../../../api/endpoints/campaigns';
import * as contactsApi from '../../../../api/endpoints/contacts';
import * as templatesApi from '../../../../api/endpoints/templates';
import * as messagesApi from '../../../../api/endpoints/messages';
import { getErrorCode, getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import {
  Alert, Button, Card, Checkbox, ConfirmDialog, FileUpload, Input, PageHeader, PageLoader, RadioCards, SearchInput, Select, Skeleton,
  StatCard, Stepper, WhatsAppPreview,
} from '../../../../components/ui';
import { templateInputs, templateToPreview } from '../../../../components/whatsapp/TemplateBuilder';
import { formatMoney, formatNumber, formatPhone, humanize } from '../../../../lib/format';
import SenderSelect from '../../common/SenderSelect';
import { defaultSenderId, useMySenders } from '../../common/useSenders';
import { buildCampaignMapping, campaignMappingComplete, guessColumn } from '../../common/columnMapping';
import { istLocalToIso, nowIstLocal } from '../components/schedule';

const STEPS = [
  { key: 'setup', label: 'Setup' },
  { key: 'map', label: 'Match data' },
  { key: 'preview', label: 'Preview & cost' },
  { key: 'send', label: 'Send' },
];

const MEDIA_ACCEPT = { IMAGE: 'image/jpeg,image/png', VIDEO: 'video/mp4', DOCUMENT: 'application/pdf' };

function ContactPicker({ value, onChange }) {
  const [search, setSearch] = useState('');
  const q = useQuery({ queryKey: ['partner', 'contacts', { search, size: 20, picker: true }], queryFn: () => contactsApi.listContacts({ search, size: 20 }) });
  return (
    <div className="rounded-xl border border-line p-3">
      <SearchInput value={search} onSearch={setSearch} placeholder="Search contacts" />
      <p className="mt-2 text-sm text-muted">{value.length} selected</p>
      <ul className="mt-2 max-h-72 divide-y divide-line overflow-y-auto">
        {q.isLoading ? (
          <Skeleton className="h-10 w-full" />
        ) : (
          (q.data?.content || []).map((c) => (
            <li key={c.id}>
              <Checkbox
                label={`${c.name || 'No name'} · ${formatPhone(c.phone)}`}
                description={c.optedOut ? 'Opted out of marketing' : undefined}
                checked={value.includes(c.id)}
                onChange={(e) => onChange(e.target.checked ? [...value, c.id] : value.filter((x) => x !== c.id))}
              />
            </li>
          ))
        )}
      </ul>
    </div>
  );
}

export default function CampaignWizardPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user } = useAuth();
  const [params] = useSearchParams();
  const senders = useMySenders();
  const [step, setStep] = useState(0);
  const [campaignId, setCampaignId] = useState(params.get('id') ? Number(params.get('id')) : null);
  const [name, setName] = useState('');
  const [senderId, setSenderId] = useState('');
  const [templateId, setTemplateId] = useState('');
  const [audienceType, setAudienceType] = useState('FILE');
  const [file, setFile] = useState(null);
  const [upload, setUpload] = useState(null); // CP1 response
  const [groupIds, setGroupIds] = useState([]);
  const [contactIds, setContactIds] = useState([]);
  const [phoneColumn, setPhoneColumn] = useState('');
  const [variables, setVariables] = useState({});
  const [headerMedia, setHeaderMedia] = useState(null);
  const [headerFile, setHeaderFile] = useState(null);
  const [defaultCountry, setDefaultCountry] = useState('IN');
  const [saveContacts, setSaveContacts] = useState(false);
  const [prepared, setPrepared] = useState(null);
  const [when, setWhen] = useState('now');
  const [scheduleLocal, setScheduleLocal] = useState(nowIstLocal(60));
  const [confirmOpen, setConfirmOpen] = useState(false);

  const existing = useQuery({ queryKey: ['partner', 'campaigns', 'detail', campaignId], queryFn: () => campaignsApi.getMyCampaign(campaignId), enabled: Boolean(campaignId) && step === 0 && !name });
  useEffect(() => {
    if (existing.data && !name) {
      setName(existing.data.name);
      if (existing.data.sender?.id) setSenderId(existing.data.sender.id);
      if (existing.data.template?.id) setTemplateId(String(existing.data.template.id));
      if (existing.data.audienceType) setAudienceType(existing.data.audienceType);
    }
  }, [existing.data, name]);
  useEffect(() => {
    if (!senderId && senders.data) setSenderId(defaultSenderId(senders.data));
  }, [senders.data, senderId]);
  const sender = (senders.data || []).find((s) => s.id === senderId);

  const templates = useQuery({
    queryKey: ['partner', 'templates', 'approved', sender?.waba?.id],
    queryFn: () => templatesApi.listMyTemplates({ status: 'APPROVED', wabaId: sender?.waba?.id, size: 100 }),
    enabled: Boolean(sender),
  });
  const templateList = templates.data?.content || [];
  const template = templateList.find((t) => String(t.id) === String(templateId));
  const inputs = template ? templateInputs(template) : null;
  const requiredVars = useMemo(() => {
    if (!inputs) return [];
    const list = [...inputs.bodyVars];
    inputs.headerVars.forEach((v) => list.push(`header.${v}`));
    inputs.buttonInputs.filter((b) => !b.otp).forEach((b) => list.push(`button.${b.index}`));
    return list;
  }, [inputs]);
  const groups = useQuery({ queryKey: ['partner', 'contact-groups', 'all'], queryFn: () => contactsApi.listContactGroups({ size: 100 }) });

  const columns = upload?.columns || [];
  useEffect(() => {
    if (upload && !phoneColumn) setPhoneColumn(guessColumn(upload.columns, 'phone'));
  }, [upload, phoneColumn]);

  const uploadM = useMutation({
    mutationFn: () => campaignsApi.uploadCampaignFile(file),
    onSuccess: (r) => {
      setUpload(r);
      setPhoneColumn(guessColumn(r.columns, 'phone'));
      toast.success(`${formatNumber(r.rowCount)} rows found`);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const audience = () => {
    if (audienceType === 'FILE') return { type: 'FILE', fileUid: upload?.fileUid };
    if (audienceType === 'GROUPS') return { type: 'GROUPS', groupIds };
    return { type: 'CONTACTS', contactIds };
  };

  const saveAndPrepare = useMutation({
    mutationFn: async () => {
      const body = {
        name: name.trim(),
        senderId,
        templateId: Number(templateId),
        audience: audience(),
        mapping: buildCampaignMapping({ phoneColumn: audienceType === 'FILE' ? phoneColumn : undefined, variables, headerMedia }),
        defaultCountry,
        saveContacts: audienceType === 'FILE' && saveContacts,
      };
      const c = campaignId ? await campaignsApi.updateCampaign(campaignId, body) : await campaignsApi.createCampaign(body);
      setCampaignId(c.id);
      return campaignsApi.prepareCampaign(c.id);
    },
    onSuccess: (p) => {
      setPrepared(p);
      setStep(2);
      qc.invalidateQueries({ queryKey: ['partner', 'campaigns'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const launch = useMutation({
    mutationFn: () => campaignsApi.launchCampaign(campaignId, { scheduleAt: when === 'later' ? istLocalToIso(scheduleLocal) : null, confirmCost: true }),
    onSuccess: (c) => {
      setConfirmOpen(false);
      toast.success(c.status === 'SCHEDULED' ? 'Campaign scheduled' : 'Campaign started');
      qc.invalidateQueries({ queryKey: ['partner'] });
      navigate(`/partner/campaigns/${c.id}`);
    },
    onError: (e) => {
      setConfirmOpen(false);
      toast.error(getErrorCode(e) === 'WALLET_INSUFFICIENT_BALANCE' ? 'Not enough balance for this campaign. Please add money.' : getErrorMessage(e));
    },
  });

  useEffect(() => {
    if (step !== 1 || !requiredVars.length) return;
    setVariables((prev) => {
      const next = { ...prev };
      requiredVars.forEach((k) => {
        if (!next[k]) {
          const guess = audienceType === 'FILE' ? guessColumn(columns, k === '1' ? 'name' : k) : '';
          next[k] = audienceType === 'FILE' ? { kind: 'column', value: guess } : { kind: 'attribute', value: k === '1' || k === 'name' ? 'name' : '' };
        }
      });
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, requiredVars.join('|'), audienceType]);

  const uploadHeaderMedia = async (f) => {
    setHeaderFile(f);
    if (!f) {
      setHeaderMedia(null);
      return;
    }
    try {
      const r = await messagesApi.uploadMedia(senderId, f);
      setHeaderMedia({ type: inputs.headerFormat.toLowerCase(), fileUid: r.fileUid, filename: f.name });
    } catch (e) {
      toast.error(getErrorMessage(e));
      setHeaderFile(null);
    }
  };

  if (campaignId && existing.isLoading && !name) return <PageLoader />;

  const audienceOk =
    audienceType === 'FILE' ? Boolean(upload?.fileUid) : audienceType === 'GROUPS' ? groupIds.length > 0 : contactIds.length > 0;
  const setupOk = name.trim().length > 0 && senderId && template && audienceOk;
  const mappingOk =
    campaignMappingComplete({ audienceType, phoneColumn, variables, required: requiredVars }) &&
    (!inputs?.needsHeaderMedia || Boolean(headerMedia?.fileUid || headerMedia?.link)) &&
    (!inputs?.needsLocation || (headerMedia?.latitude !== undefined && headerMedia?.longitude !== undefined));
  const est = prepared?.estimate;
  const insufficient = est && est.sufficient === false;
  const scheduleIso = when === 'later' ? istLocalToIso(scheduleLocal) : null;
  const scheduleOk = when === 'now' || (scheduleIso && new Date(scheduleIso).getTime() > Date.now() + 60000);

  const varLabel = (k) => {
    if (k.startsWith('header.')) return `Header {{${k.slice(7)}}}`;
    if (k.startsWith('button.')) return inputs.buttonInputs.find((b) => `button.${b.index}` === k)?.label || 'Button value';
    return inputs?.auth ? 'One-time code' : `{{${k}}}`;
  };
  const sourceOptions = [
    ...(audienceType === 'FILE' ? [{ value: 'column', label: 'Column from file' }] : []),
    { value: 'attribute', label: 'Contact field' },
    { value: 'static', label: 'Same text for everyone' },
  ];

  return (
    <>
      <PageHeader title="New campaign" subtitle="Send an approved template to many people" backTo="/partner/campaigns" backLabel="Campaigns" />
      <Stepper steps={STEPS} current={step} className="mb-6" />
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-5">
          {step === 0 && (
            <>
              <Card title="Campaign details">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <Input className="sm:col-span-2" label="Campaign name" maxLength={150} value={name} onChange={(e) => setName(e.target.value)} placeholder="Diwali offer 2026" required />
                  <SenderSelect value={senderId} onChange={(v) => { setSenderId(v); setTemplateId(''); }} />
                  {templates.isLoading && sender ? (
                    <Skeleton className="h-11 w-full self-end" />
                  ) : (
                    <Select
                      label="Template"
                      value={templateId}
                      onChange={(e) => { setTemplateId(e.target.value); setVariables({}); setHeaderMedia(null); setHeaderFile(null); }}
                      options={templateList.map((t) => ({ value: t.id, label: `${t.name} (${t.language}) · ${humanize(t.category)}` }))}
                      placeholder={templateList.length ? 'Choose an approved template' : 'No approved templates'}
                      required
                    />
                  )}
                </div>
                {sender && !templates.isLoading && !templateList.length && (
                  <Alert variant="info" className="mt-4" action={<Button size="sm" to="/partner/templates/new">Create template</Button>}>
                    You need an approved template for this number.
                  </Alert>
                )}
              </Card>
              <Card title="Who should receive it?">
                <RadioCards
                  name="audience"
                  columns={3}
                  value={audienceType}
                  onChange={setAudienceType}
                  options={[
                    { value: 'FILE', label: 'Upload a file', description: 'CSV or Excel', icon: FileSpreadsheet },
                    { value: 'GROUPS', label: 'Contact groups', description: 'Saved groups', icon: Users },
                    { value: 'CONTACTS', label: 'Pick contacts', description: 'Choose people', icon: UserSquare2 },
                  ]}
                />
                <div className="mt-4">
                  {audienceType === 'FILE' && (
                    <div className="space-y-3">
                      <FileUpload accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" maxSizeMB={10} value={file} onChange={(f) => { setFile(f); setUpload(null); setPhoneColumn(''); }} hint="CSV or Excel · up to 1,00,000 rows · max 10 MB · one row per person" />
                      {file && !upload && <Button onClick={() => uploadM.mutate()} loading={uploadM.isPending}>Upload file</Button>}
                      {upload && <Alert variant="success">{formatNumber(upload.rowCount)} rows and {upload.columns.length} columns found.</Alert>}
                    </div>
                  )}
                  {audienceType === 'GROUPS' && (
                    (groups.data?.content || []).length ? (
                      <div className="space-y-1">
                        {(groups.data?.content || []).map((g) => (
                          <Checkbox key={g.id} label={`${g.name} (${formatNumber(g.memberCount)})`} checked={groupIds.includes(g.id)} onChange={(e) => setGroupIds(e.target.checked ? [...groupIds, g.id] : groupIds.filter((x) => x !== g.id))} />
                        ))}
                      </div>
                    ) : (
                      <Alert variant="info" action={<Button size="sm" to="/partner/contacts/groups">Create a group</Button>}>You have no contact groups yet.</Alert>
                    )
                  )}
                  {audienceType === 'CONTACTS' && <ContactPicker value={contactIds} onChange={setContactIds} />}
                </div>
              </Card>
              <div className="flex justify-end">
                <Button onClick={() => setStep(1)} disabled={!setupOk}>Continue</Button>
              </div>
            </>
          )}

          {step === 1 && inputs && (
            <>
              {audienceType === 'FILE' && (
                <Card title="Phone numbers">
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <Select label="Column with WhatsApp numbers" value={phoneColumn} onChange={(e) => setPhoneColumn(e.target.value)} options={columns.map((c) => ({ value: c, label: c }))} placeholder="Choose column" required />
                    <Select
                      label="Numbers without country code are from"
                      value={defaultCountry}
                      onChange={(e) => setDefaultCountry(e.target.value)}
                      options={[{ value: 'IN', label: 'India (+91)' }, { value: 'AE', label: 'UAE (+971)' }, { value: 'US', label: 'USA (+1)' }, { value: 'GB', label: 'UK (+44)' }]}
                    />
                  </div>
                  <Checkbox className="mt-3" label="Save these people to my contacts" checked={saveContacts} onChange={(e) => setSaveContacts(e.target.checked)} />
                </Card>
              )}
              {requiredVars.length > 0 && (
                <Card title="Fill the template variables" subtitle="Choose where each value comes from">
                  <div className="space-y-4">
                    {requiredVars.map((k) => {
                      const v = variables[k] || { kind: sourceOptions[0].value, value: '' };
                      return (
                        <div key={k} className="grid gap-2 rounded-xl border border-line p-3 sm:grid-cols-[140px_200px_minmax(0,1fr)] sm:items-end">
                          <p className="text-sm font-semibold text-ink sm:pb-3">{varLabel(k)}</p>
                          <Select aria-label="Source" value={v.kind} onChange={(e) => setVariables({ ...variables, [k]: { kind: e.target.value, value: '' } })} options={sourceOptions} />
                          {v.kind === 'column' ? (
                            <Select aria-label="Column" value={v.value} onChange={(e) => setVariables({ ...variables, [k]: { ...v, value: e.target.value } })} options={columns.map((c) => ({ value: c, label: c }))} placeholder="Choose column" />
                          ) : v.kind === 'attribute' ? (
                            <Input aria-label="Contact field" placeholder="name or custom field e.g. city" value={v.value} onChange={(e) => setVariables({ ...variables, [k]: { ...v, value: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '_') } })} />
                          ) : (
                            <Input aria-label="Text" placeholder="Text for everyone" value={v.value} onChange={(e) => setVariables({ ...variables, [k]: { ...v, value: e.target.value } })} />
                          )}
                        </div>
                      );
                    })}
                  </div>
                </Card>
              )}
              {inputs.needsHeaderMedia && (
                <Card title={`Header ${inputs.headerFormat.toLowerCase()}`} subtitle="The same file is sent to everyone">
                  <FileUpload accept={MEDIA_ACCEPT[inputs.headerFormat]} maxSizeMB={inputs.headerFormat === 'IMAGE' ? 5 : 16} value={headerFile} onChange={uploadHeaderMedia} />
                  {!headerFile && (
                    <Input className="mt-2" label="…or a public link (https)" value={headerMedia?.link || ''} onChange={(e) => setHeaderMedia({ type: inputs.headerFormat.toLowerCase(), link: e.target.value })} />
                  )}
                </Card>
              )}
              {inputs.needsLocation && (
                <Card title="Header location">
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <Input label="Latitude" inputMode="decimal" value={headerMedia?.latitude ?? ''} onChange={(e) => setHeaderMedia({ ...(headerMedia || {}), type: 'location', latitude: e.target.value })} />
                    <Input label="Longitude" inputMode="decimal" value={headerMedia?.longitude ?? ''} onChange={(e) => setHeaderMedia({ ...(headerMedia || {}), type: 'location', longitude: e.target.value })} />
                    <Input label="Place name" value={headerMedia?.name || ''} onChange={(e) => setHeaderMedia({ ...(headerMedia || {}), type: 'location', name: e.target.value })} />
                    <Input label="Address" value={headerMedia?.address || ''} onChange={(e) => setHeaderMedia({ ...(headerMedia || {}), type: 'location', address: e.target.value })} />
                  </div>
                </Card>
              )}
              {requiredVars.length === 0 && audienceType !== 'FILE' && !inputs.needsHeaderMedia && !inputs.needsLocation && (
                <Alert variant="info">This template has no variables. Nothing to fill.</Alert>
              )}
              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setStep(0)}>Back</Button>
                <Button onClick={() => saveAndPrepare.mutate()} loading={saveAndPrepare.isPending} disabled={!mappingOk}>Check list and cost</Button>
              </div>
            </>
          )}

          {step === 2 && prepared && (
            <>
              <Card title="Your list">
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <StatCard label="Will receive" tone="green" value={formatNumber(prepared.counts?.valid)} />
                  <StatCard label="Rows checked" value={formatNumber(prepared.counts?.total)} />
                  <StatCard label="Invalid numbers" tone="red" value={formatNumber(prepared.counts?.invalid)} />
                  <StatCard label="Duplicates removed" tone="amber" value={formatNumber(prepared.counts?.duplicates)} />
                  <StatCard label="Opted out (skipped)" tone="amber" value={formatNumber(prepared.counts?.optedOut)} />
                  <StatCard label="Blocked (skipped)" tone="red" value={formatNumber(prepared.counts?.blocked)} />
                </div>
              </Card>
              {prepared.preview?.length > 0 && (
                <Card title="How the first messages look">
                  <ul className="space-y-3">
                    {prepared.preview.map((p, i) => (
                      <li key={i} className="rounded-lg bg-surface-2 p-3 text-sm">
                        <p className="mb-1 text-xs font-medium text-muted">{formatPhone(p.phone)}</p>
                        <p className="whitespace-pre-wrap text-ink">{p.renderedText}</p>
                      </li>
                    ))}
                  </ul>
                </Card>
              )}
              <Card title="Cost">
                {est ? (
                  <>
                    <dl className="space-y-1.5 text-sm">
                      {(est.countryBreakdown || []).map((b) => (
                        <div key={b.countryCode} className="flex justify-between gap-2">
                          <dt className="text-muted">{b.countryCode}: {formatNumber(b.count)} × {formatMoney(b.unitPrice, { precise: true })}</dt>
                          <dd className="tabular-nums">{formatMoney(b.subtotal)}</dd>
                        </div>
                      ))}
                      <div className="flex justify-between border-t border-line pt-2 text-base font-semibold"><dt>Total</dt><dd>{formatMoney(est.total)}</dd></div>
                      <div className="flex justify-between text-muted"><dt>Wallet balance</dt><dd>{formatMoney(est.available)}</dd></div>
                      <div className="flex justify-between text-muted"><dt>Balance after</dt><dd>{formatMoney(est.balanceAfter)}</dd></div>
                    </dl>
                    <p className="mt-2 text-xs text-muted">Money is held per message and returned for messages that fail.</p>
                    {insufficient && (
                      <Alert variant="error" className="mt-3" title="Not enough balance" action={<Button size="sm" to="/partner/wallet/add">Add money</Button>}>
                        Add at least {formatMoney(Number(est.total) - Number(est.available))} to send this campaign.
                      </Alert>
                    )}
                  </>
                ) : (
                  <p className="text-sm text-muted">Cost is not available.</p>
                )}
              </Card>
              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setStep(1)}>Back</Button>
                <Button onClick={() => setStep(3)} disabled={!prepared.counts?.valid || insufficient}>Continue</Button>
              </div>
            </>
          )}

          {step === 3 && (
            <>
              <Card title="When should we send it?">
                <RadioCards
                  name="when"
                  columns={2}
                  value={when}
                  onChange={setWhen}
                  options={[
                    { value: 'now', label: 'Send now', description: 'Starts within a minute', icon: Send },
                    { value: 'later', label: 'Schedule', description: 'Pick a date and time (IST)', icon: CalendarClock },
                  ]}
                />
                {when === 'later' && (
                  <Input
                    className="mt-4"
                    label="Date and time (India time)"
                    type="datetime-local"
                    min={nowIstLocal(2)}
                    value={scheduleLocal}
                    onChange={(e) => setScheduleLocal(e.target.value)}
                    error={!scheduleOk ? 'Choose a time in the future' : undefined}
                  />
                )}
              </Card>
              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setStep(2)}>Back</Button>
                <Button size="lg" leftIcon={when === 'now' ? Send : CalendarClock} onClick={() => setConfirmOpen(true)} disabled={!scheduleOk}>
                  {when === 'now' ? 'Send campaign' : 'Schedule campaign'}
                </Button>
              </div>
            </>
          )}
        </div>
        <div className="lg:sticky lg:top-4 lg:self-start">
          <p className="mb-2 text-sm font-medium text-muted">Template</p>
          {template ? (
            <WhatsAppPreview {...templateToPreview(template)} businessName={sender?.verifiedName || user?.partner?.businessName} />
          ) : (
            <div className="card p-6 text-center text-sm text-muted">Choose a template to see it here.</div>
          )}
        </div>
      </div>
      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={() => launch.mutate()}
        loading={launch.isPending}
        title={when === 'now' ? 'Send this campaign now?' : 'Schedule this campaign?'}
        confirmLabel={when === 'now' ? 'Yes, send' : 'Yes, schedule'}
        message={
          <>
            <strong>{formatNumber(prepared?.counts?.valid)}</strong> people will receive <strong>{template?.name}</strong>. Estimated cost{' '}
            <strong>{formatMoney(est?.total)}</strong>.
          </>
        }
      />
    </>
  );
}
