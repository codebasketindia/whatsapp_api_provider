import { PageLoader } from './Spinner';
import ErrorState from './ErrorState';

/**
 * Renders loading / error / children for a TanStack query.
 * <QueryState query={q}>{(data) => ...}</QueryState>
 */
export default function QueryState({ query, children, loader = <PageLoader />, errorTitle }) {
  if (query.isLoading) return loader;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} title={errorTitle} />;
  return typeof children === 'function' ? children(query.data) : children;
}
