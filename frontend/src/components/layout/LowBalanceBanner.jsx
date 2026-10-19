import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import * as walletApi from '../../api/endpoints/wallet';
import { formatMoney } from '../../lib/format';
import { Banner } from '../ui/Alert';

/** Warns the partner when the wallet is below their threshold. */
export default function LowBalanceBanner() {
  const { data } = useQuery({ queryKey: ['partner', 'wallet'], queryFn: walletApi.getMyWallet, refetchInterval: 120_000 });
  if (!data?.isLow) return null;
  return (
    <Banner
      variant="warning"
      action={
        <Link to="/partner/wallet/add" className="inline-flex min-h-[36px] items-center rounded-lg bg-amber-600 px-3 font-medium text-white hover:bg-amber-700">
          Add money
        </Link>
      }
    >
      Low balance: <strong>{formatMoney(data.balance)}</strong>. Messages stop when the balance runs out.
    </Banner>
  );
}
