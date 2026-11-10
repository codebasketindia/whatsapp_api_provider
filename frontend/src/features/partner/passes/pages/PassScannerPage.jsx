import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Camera, CameraOff, ScanLine } from 'lucide-react';
import toast from 'react-hot-toast';
import * as regApi from '../../../../api/endpoints/registrations';
import { getErrorMessage } from '../../../../api/axios';
import { Button, Card, Input, PageHeader } from '../../../../components/ui';
import PassStatusCard from '../../../public/register/PassStatusCard';

/** PV2/PV3: entry desk — scan or paste a pass QR, see who it belongs to, mark it used (only once). */
export default function PassScannerPage() {
  const [params] = useSearchParams();
  const [token, setToken] = useState(params.get('token') || '');
  const [result, setResult] = useState(null);
  const [redeemedNow, setRedeemedNow] = useState(false);
  const [scanning, setScanning] = useState(false);
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const canScan = typeof window !== 'undefined' && 'BarcodeDetector' in window && navigator.mediaDevices?.getUserMedia;

  const verify = useMutation({
    mutationFn: (t) => regApi.verifyPass(t),
    onSuccess: (r) => { setResult(r); setRedeemedNow(false); },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const redeem = useMutation({
    mutationFn: () => regApi.redeemPass(token),
    onSuccess: (r) => { setResult(r); setRedeemedNow(r.status === 'VALID'); },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const stop = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setScanning(false);
  };
  useEffect(() => () => stop(), []);
  useEffect(() => {
    if (params.get('token')) verify.mutate(params.get('token'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const start = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      streamRef.current = stream;
      setScanning(true);
      const video = videoRef.current;
      video.srcObject = stream;
      await video.play();
      const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
      const loop = async () => {
        if (!streamRef.current) return;
        const codes = await detector.detect(video).catch(() => []);
        if (codes[0]?.rawValue) {
          setToken(codes[0].rawValue);
          stop();
          verify.mutate(codes[0].rawValue);
          return;
        }
        requestAnimationFrame(loop);
      };
      loop();
    } catch {
      toast.error('Camera is not available. Paste the pass link instead.');
      stop();
    }
  };

  return (
    <>
      <PageHeader title="Verify passes" subtitle="Scan the QR code on a pass. A pass can be used only once." />
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <Card>
          {canScan && (
            <div className="mb-4">
              <video ref={videoRef} className={`aspect-square w-full rounded-xl bg-black object-cover ${scanning ? '' : 'hidden'}`} muted playsInline />
              <Button fullWidth variant={scanning ? 'secondary' : 'primary'} leftIcon={scanning ? CameraOff : Camera} onClick={scanning ? stop : start}>
                {scanning ? 'Stop camera' : 'Scan with camera'}
              </Button>
            </div>
          )}
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); if (token.trim()) verify.mutate(token.trim()); }}>
            <Input label="Pass link or code" value={token} onChange={(e) => setToken(e.target.value)} placeholder="https://…/v/123.AbC…" />
            <Button type="submit" fullWidth variant="secondary" leftIcon={ScanLine} loading={verify.isPending} disabled={!token.trim()}>Check pass</Button>
          </form>
        </Card>
        <div className="space-y-3">
          {result ? (
            <>
              <PassStatusCard result={result} redeemedNow={redeemedNow} />
              {result.status === 'VALID' && !redeemedNow && (
                <Button fullWidth size="lg" loading={redeem.isPending} onClick={() => redeem.mutate()}>Allow entry — mark as used</Button>
              )}
            </>
          ) : (
            <Card><p className="text-sm text-muted">The result appears here: valid, already used or not valid.</p></Card>
          )}
        </div>
      </div>
    </>
  );
}
