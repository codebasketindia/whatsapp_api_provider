import { useEffect } from 'react';
import { APP_NAME } from '../lib/constants';

export function usePageTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · ${APP_NAME}` : APP_NAME;
  }, [title]);
}
