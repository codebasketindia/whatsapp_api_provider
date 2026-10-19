import { Link } from 'react-router-dom';
import { Home, LockKeyhole, SearchX, ServerCrash } from 'lucide-react';
import { useAuth } from '../auth/useAuth';
import { homePathFor } from '../auth/AuthProvider';
import { usePageTitle } from '../hooks/usePageTitle';
import { Button } from '../components/ui';

const VARIANTS = {
  403: { icon: LockKeyhole, title: 'You don’t have access', text: 'This page is not available for your account. Ask your administrator if you need access.' },
  404: { icon: SearchX, title: 'Page not found', text: 'The page you are looking for does not exist or has moved.' },
  500: { icon: ServerCrash, title: 'Something went wrong', text: 'An unexpected error happened. Please try again in a moment.' },
};

export default function ErrorPage({ code = 404, onReset }) {
  const v = VARIANTS[code] || VARIANTS[404];
  usePageTitle(v.title);
  let home = '/';
  try {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const { user } = useAuth();
    home = user ? homePathFor(user) : '/login';
  } catch {
    home = '/';
  }
  return (
    <div className="flex min-h-[100dvh] flex-col items-center justify-center bg-canvas px-6 text-center">
      <span className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
        <v.icon className="h-8 w-8" aria-hidden />
      </span>
      <p className="text-sm font-semibold text-muted">Error {code}</p>
      <h1 className="mt-1 text-2xl font-semibold text-ink">{v.title}</h1>
      <p className="mt-2 max-w-md text-sm text-muted">{v.text}</p>
      <div className="mt-6 flex flex-wrap justify-center gap-2">
        {onReset && (
          <Button variant="secondary" onClick={onReset}>
            Try again
          </Button>
        )}
        {onReset ? (
          <Button leftIcon={Home} onClick={() => window.location.assign(home)}>
            Go to dashboard
          </Button>
        ) : (
          <Button leftIcon={Home} to={home}>
            Go to dashboard
          </Button>
        )}
      </div>
      {!onReset && (
        <Link to="/login" className="mt-4 text-sm text-muted hover:text-ink">
          Log in with another account
        </Link>
      )}
    </div>
  );
}

export const ForbiddenPage = () => <ErrorPage code={403} />;
export const NotFoundPage = () => <ErrorPage code={404} />;
export const ServerErrorPage = () => <ErrorPage code={500} />;
