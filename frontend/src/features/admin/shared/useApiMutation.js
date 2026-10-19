import { useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../api/axios';

/**
 * useMutation with the admin conventions: success toast, error toast and
 * query invalidation by key prefixes. `success` may be a string or (data, vars) => string.
 */
export function useApiMutation({ mutationFn, success, invalidate = [], onSuccess, onError }) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (data, vars, ctx) => {
      const msg = typeof success === 'function' ? success(data, vars) : success;
      if (msg) toast.success(msg);
      invalidate.forEach((key) => qc.invalidateQueries({ queryKey: key }));
      onSuccess?.(data, vars, ctx);
    },
    onError: (err, vars, ctx) => {
      toast.error(getErrorMessage(err));
      onError?.(err, vars, ctx);
    },
  });
}
