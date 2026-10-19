import { useEffect, useState } from 'react';
import { Search, X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useDebounce } from '../../hooks/useDebounce';

/** Debounced search box; calls onSearch(value) after typing pauses. */
export default function SearchInput({ value = '', onSearch, placeholder = 'Search…', delay = 350, className, autoFocus }) {
  const [text, setText] = useState(value);
  const debounced = useDebounce(text, delay);
  useEffect(() => {
    setText(value || '');
  }, [value]);
  useEffect(() => {
    if ((debounced || '') !== (value || '')) onSearch?.(debounced);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);
  return (
    <div className={cn('relative', className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      <input
        type="search"
        value={text}
        autoFocus={autoFocus}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="field-base pl-9 pr-10 [&::-webkit-search-cancel-button]:hidden"
      />
      {text && (
        <button
          type="button"
          onClick={() => {
            setText('');
            onSearch?.('');
          }}
          className="absolute right-1 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md text-muted hover:text-ink"
          aria-label="Clear search"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
