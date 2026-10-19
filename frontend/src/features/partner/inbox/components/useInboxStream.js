import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import * as messagesApi from '../../../../api/endpoints/messages';
import { API_BASE_URL } from '../../../../api/axios';

/**
 * Live inbox updates over Server-Sent Events. EventSource cannot send an Authorization header,
 * so we first get a short-lived ticket (IN6) and open /api/public/inbox-stream?ticket=…
 * Returns { live } — when false, callers should poll.
 */
export function useInboxStream() {
  const qc = useQueryClient();
  const [live, setLive] = useState(false);
  const retries = useRef(0);
  useEffect(() => {
    if (typeof window === 'undefined' || !window.EventSource) return undefined;
    let es;
    let timer;
    let closed = false;
    const onEvent = () => {
      qc.invalidateQueries({ queryKey: ['partner', 'inbox'] });
    };
    const connect = async () => {
      try {
        const { ticket } = await messagesApi.getInboxStreamTicket();
        if (closed) return;
        es = new EventSource(`${API_BASE_URL}/public/inbox-stream?ticket=${encodeURIComponent(ticket)}`);
        es.onopen = () => {
          retries.current = 0;
          setLive(true);
        };
        es.addEventListener('message.new', onEvent);
        es.addEventListener('message.status', onEvent);
        es.onmessage = onEvent;
        es.onerror = () => {
          setLive(false);
          es.close();
          if (!closed && retries.current < 5) {
            retries.current += 1;
            timer = setTimeout(connect, 2000 * retries.current);
          }
        };
      } catch {
        setLive(false);
      }
    };
    connect();
    return () => {
      closed = true;
      clearTimeout(timer);
      if (es) es.close();
    };
  }, [qc]);
  return { live };
}
