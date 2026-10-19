import { api, cleanParams, downloadFile, fetchObjectUrl, toFormData, unwrap } from '../axios';

/**
 * T1–T21: same contract under /admin/senders/{id} and /partner/senders/{id}.
 * Usage: const tools = senderTools('partner'); tools.getProfile(id)
 */
export function senderTools(scope) {
  if (scope !== 'admin' && scope !== 'partner') throw new Error(`Unknown scope ${scope}`);
  const base = (id) => `/${scope}/senders/${id}`;
  return {
    scope,
    sync: (id) => api.post(`${base(id)}/sync`).then(unwrap),
    requestCode: (id, body) => api.post(`${base(id)}/request-code`, body).then(unwrap),
    verifyCode: (id, code) => api.post(`${base(id)}/verify-code`, { code }).then(unwrap),
    register: (id, pin) => api.post(`${base(id)}/register`, { pin }).then(unwrap),
    deregister: (id) => api.post(`${base(id)}/deregister`, { confirm: true }).then(unwrap),
    changePin: (id, pin) => api.put(`${base(id)}/two-step-pin`, { pin }).then(unwrap),
    getProfile: (id) => api.get(`${base(id)}/profile`).then(unwrap),
    updateProfile: (id, body) => api.put(`${base(id)}/profile`, body).then(unwrap),
    uploadProfilePhoto: (id, file) =>
      api
        .post(`${base(id)}/profile/photo`, toFormData({ file }), { headers: { 'Content-Type': 'multipart/form-data' } })
        .then(unwrap),
    getHealth: (id) => api.get(`${base(id)}/health`).then(unwrap),
    getAnalytics: (id, params) => api.get(`${base(id)}/analytics`, { params: cleanParams(params) }).then(unwrap),
    listQrCodes: (id) => api.get(`${base(id)}/qr-codes`).then(unwrap),
    createQrCode: (id, prefilledMessage) => api.post(`${base(id)}/qr-codes`, { prefilledMessage }).then(unwrap),
    updateQrCode: (id, qrId, prefilledMessage) => api.put(`${base(id)}/qr-codes/${qrId}`, { prefilledMessage }).then(unwrap),
    deleteQrCode: (id, qrId) => api.delete(`${base(id)}/qr-codes/${qrId}`).then(unwrap),
    qrImageUrl: (id, qrId, format = 'png') => fetchObjectUrl(`${base(id)}/qr-codes/${qrId}/image`, { format }),
    downloadQrImage: (id, qrId, format = 'png') =>
      downloadFile(`${base(id)}/qr-codes/${qrId}/image`, { format }, `qr-${qrId}.${format}`),
    getConversationalComponents: (id) => api.get(`${base(id)}/conversational-components`).then(unwrap),
    updateConversationalComponents: (id, body) => api.put(`${base(id)}/conversational-components`, body).then(unwrap),
    listBlockedUsers: (id, params) => api.get(`${base(id)}/blocked-users`, { params: cleanParams(params) }).then(unwrap),
    blockUsers: (id, waIds) => api.post(`${base(id)}/blocked-users`, { waIds }).then(unwrap),
    unblockUsers: (id, waIds) => api.delete(`${base(id)}/blocked-users`, { data: { waIds } }).then(unwrap),
  };
}

export const adminSenderTools = senderTools('admin');
export const partnerSenderTools = senderTools('partner');
