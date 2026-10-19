import { Suspense } from 'react';
import { RouterProvider } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'react-hot-toast';
import { queryClient } from './api/queryClient';
import { AuthProvider } from './auth/AuthProvider';
import { ThemeProvider } from './hooks/useTheme';
import { router } from './router';
import ErrorBoundary from './pages/ErrorBoundary';
import { FullPageLoader } from './components/ui/Spinner';

export default function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AuthProvider>
            <Suspense fallback={<FullPageLoader />}>
              <RouterProvider router={router} future={{ v7_startTransition: true }} />
            </Suspense>
            <Toaster
              position="top-center"
              toastOptions={{
                duration: 4000,
                className: '!bg-surface !text-ink !border !border-line !shadow-lg !text-sm',
                success: { iconTheme: { primary: '#25D366', secondary: '#fff' } },
              }}
            />
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
