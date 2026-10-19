import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download, ExternalLink } from 'lucide-react';
import toast from 'react-hot-toast';
import * as developerApi from '../../../../api/endpoints/developer';
import { getErrorMessage } from '../../../../api/axios';
import { Badge, Button, Card, CopyButton, KeyValue, Skeleton } from '../../../../components/ui';
import { cn } from '../../../../lib/cn';
import { EXAMPLES, ERROR_CODES, LANGUAGES, LANGUAGE_LABEL, PUBLIC_ENDPOINTS, SAMPLE_RESPONSE, SIGNATURE_SAMPLES, codeSample } from './codeSamples';

function CodeBlock({ code }) {
  return (
    <div className="relative">
      <pre className="max-h-96 overflow-auto rounded-lg bg-[#0b141a] p-4 pr-12 text-xs leading-relaxed text-[#e9edef]"><code>{code}</code></pre>
      <div className="absolute right-2 top-2 rounded-md bg-white/90 dark:bg-surface"><CopyButton value={code} toastMessage="Code copied" /></div>
    </div>
  );
}

function LangTabs({ value, onChange }) {
  return (
    <div className="mb-2 flex flex-wrap gap-1" role="tablist">
      {LANGUAGES.map((l) => (
        <button
          key={l}
          type="button"
          role="tab"
          aria-selected={value === l}
          onClick={() => onChange(l)}
          className={cn('min-h-[36px] rounded-full px-3 text-sm', value === l ? 'bg-brand-600 text-white' : 'bg-surface-2 text-ink hover:bg-line/60')}
        >
          {LANGUAGE_LABEL[l]}
        </button>
      ))}
    </div>
  );
}

export default function ApiDocsTab() {
  const info = useQuery({ queryKey: ['partner', 'developer', 'info'], queryFn: developerApi.getDeveloperInfo });
  const [lang, setLang] = useState('curl');
  const [example, setExample] = useState(EXAMPLES[0].key);
  const baseUrl = info.data?.baseUrl || `${window.location.origin}/api/v1/public`;
  const ex = EXAMPLES.find((e) => e.key === example);
  return (
    <div className="space-y-5">
      <Card title="Getting started">
        {info.isLoading ? (
          <Skeleton className="h-20 w-full" />
        ) : (
          <KeyValue
            items={[
              { label: 'Base URL', value: <span className="flex items-center gap-1 break-all font-mono text-xs">{baseUrl}<CopyButton value={baseUrl} /></span> },
              { label: 'Rate limit', value: info.data?.rateLimitPerMinute ? `${info.data.rateLimitPerMinute} requests per minute per key` : '—' },
              { label: 'Authentication', value: <span className="font-mono text-xs">X-API-KEY and X-API-SECRET headers</span> },
              { label: 'Response format', value: <span className="font-mono text-xs">{'{ success, code, message, data }'}</span> },
            ]}
          />
        )}
        <div className="mt-4 flex flex-wrap gap-2">
          {info.data?.swaggerUrl && <Button variant="secondary" size="sm" rightIcon={ExternalLink} href={info.data.swaggerUrl}>Swagger reference</Button>}
          <Button variant="secondary" size="sm" leftIcon={Download} onClick={() => developerApi.downloadPostmanCollection().catch((e) => toast.error(getErrorMessage(e)))}>Postman collection</Button>
        </div>
        <p className="mt-3 text-xs text-muted">Tip: send an Idempotency-Key header on POST requests so a retry never sends the same message twice.</p>
      </Card>

      <Card title="Endpoints">
        <ul className="divide-y divide-line">
          {PUBLIC_ENDPOINTS.map(([m, p, d]) => (
            <li key={`${m}${p}`} className="flex flex-wrap items-center gap-2 py-2 text-sm">
              <Badge tone={m === 'GET' ? 'blue' : 'green'}>{m}</Badge>
              <span className="font-mono text-xs">{p}</span>
              <span className="text-muted">— {d}</span>
            </li>
          ))}
        </ul>
      </Card>

      <Card title="Code examples">
        <div className="mb-3 flex flex-wrap gap-1">
          {EXAMPLES.map((e) => (
            <Button key={e.key} size="sm" variant={example === e.key ? 'primary' : 'secondary'} onClick={() => setExample(e.key)}>{e.label}</Button>
          ))}
        </div>
        <LangTabs value={lang} onChange={setLang} />
        <CodeBlock code={codeSample(lang, ex, baseUrl)} />
        <p className="label-base mt-4">Example response</p>
        <CodeBlock code={JSON.stringify(SAMPLE_RESPONSE.send, null, 2)} />
      </Card>

      <Card title="Webhook events" subtitle="We POST JSON to your URL. Reply with any 2xx within 10 seconds. We retry 5 times (1 min, 5 min, 15 min, 1 h, 6 h).">
        <CodeBlock code={JSON.stringify(SAMPLE_RESPONSE.status, null, 2)} />
        <p className="label-base mt-4">Check the signature</p>
        <LangTabs value={lang} onChange={setLang} />
        <CodeBlock code={SIGNATURE_SAMPLES[lang]} />
      </Card>

      <Card title="Error codes">
        <ul className="divide-y divide-line text-sm">
          {ERROR_CODES.map(([code, http, text]) => (
            <li key={code} className="flex flex-wrap items-center gap-2 py-2">
              <span className="font-mono text-xs font-semibold">{code}</span>
              <Badge tone="gray">{http}</Badge>
              <span className="text-muted">{text}</span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
