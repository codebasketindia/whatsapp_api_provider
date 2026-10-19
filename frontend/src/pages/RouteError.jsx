import { isRouteErrorResponse, useRouteError } from 'react-router-dom';
import ErrorPage from './ErrorPage';

/** Router-level error element (loader/render errors, failed lazy chunks). */
export default function RouteError() {
  const error = useRouteError();
  if (isRouteErrorResponse(error) && error.status === 404) return <ErrorPage code={404} />;
  console.error('Route error', error);
  const chunkFailed = /dynamically imported module|Loading chunk|Failed to fetch/i.test(String(error?.message));
  return <ErrorPage code={500} onReset={() => window.location.reload()} key={chunkFailed ? 'chunk' : 'err'} />;
}
