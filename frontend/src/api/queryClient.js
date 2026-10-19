import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (count, error) => {
        const status = error?.response?.status;
        if (status && status >= 400 && status < 500) return false;
        return count < 1;
      },
    },
    mutations: { retry: false },
  },
});
