import { flattenForDiff } from './diff';

/** Field-by-field comparison of two JSON values (old → new). Changed rows are highlighted. */
export default function JsonDiff({ before, after }) {
  const rows = flattenForDiff(before, after);
  if (rows.length === 0) return <p className="text-sm text-muted">No values recorded for this action.</p>;
  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="min-w-full text-sm">
        <thead className="bg-surface-2 text-left text-muted">
          <tr>
            <th className="px-3 py-2 font-medium">Field</th>
            <th className="px-3 py-2 font-medium">Before</th>
            <th className="px-3 py-2 font-medium">After</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((r) => (
            <tr key={r.path} className={r.changed ? 'bg-amber-50 dark:bg-amber-500/10' : undefined}>
              <td className="whitespace-nowrap px-3 py-2 font-mono text-xs text-muted">{r.path}</td>
              <td className="break-all px-3 py-2 font-mono text-xs text-red-700 dark:text-red-300">{r.before}</td>
              <td className="break-all px-3 py-2 font-mono text-xs text-green-700 dark:text-green-300">{r.after}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
