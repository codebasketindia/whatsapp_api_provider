// Minimal local stand-in for graph.facebook.com used only for end-to-end UI checks.
import http from 'node:http';
const json = (res, code, body) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  const path = u.pathname.replace(/^\/v\d+\.\d+/, '');
  const fields = u.searchParams.get('fields') || '';
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    console.log(req.method, path, fields.slice(0, 60));
    if (path === '/debug_token') return json(res, 200, { data: { is_valid: true, expires_at: 0, scopes: ['whatsapp_business_management', 'whatsapp_business_messaging'] } });
    if (path.endsWith('/whatsapp_business_profile')) return req.method === 'GET'
      ? json(res, 200, { data: [{ about: 'Fast delivery', description: 'Wholesale traders', address: 'MG Road, Pune', email: 'hello@ravi.example', websites: ['https://ravi.example'], vertical: 'RETAIL', profile_picture_url: null }] })
      : json(res, 200, { success: true });
    if (path.endsWith('/message_qrdls')) return json(res, 200, { data: [] });
    if (path.endsWith('/block_users')) return json(res, 200, { data: [] });
    if (path.endsWith('/messages')) return json(res, 200, { messaging_product: 'whatsapp', success: true, messages: [{ id: 'wamid.STUB' + Date.now() }] });
    if (path.endsWith('/subscribed_apps')) return json(res, 200, { success: true, data: [] });
    if (path.endsWith('/message_templates')) return json(res, 200, { data: [], paging: {} });
    if (path.endsWith('/template_analytics')) return json(res, 200, { data: [] });
    if (path === '/message_template_library') return json(res, 200, { data: [{ name: 'order_shipped', category: 'UTILITY', topic: 'ORDER_MANAGEMENT', usecase: 'SHIPMENT', language: 'en_US', body: 'Your order {{1}} has shipped.', buttons: [] }] });
    if (path.endsWith('/phone_numbers')) return json(res, 200, { data: [] });
    if (fields.includes('conversational_automation')) return json(res, 200, { conversational_automation: { enable_welcome_message: false, prompts: [], commands: [] }, id: path.slice(1) });
    if (fields.includes('pricing_analytics')) return json(res, 200, { pricing_analytics: { data: [] }, id: path.slice(1) });
    if (fields.includes('health_status') || fields.includes('quality_rating')) return json(res, 200, {
      id: path.slice(1), quality_rating: 'GREEN', messaging_limit_tier: 'TIER_1K', throughput: { level: 'STANDARD' }, name_status: 'APPROVED',
      display_phone_number: '+91 98765 43210', verified_name: 'Ravi Traders', code_verification_status: 'VERIFIED', status: 'CONNECTED', platform_type: 'CLOUD_API',
      health_status: { can_send_message: 'AVAILABLE', entities: [] } });
    if (req.method === 'GET' && /^\/\d+$/.test(path)) return json(res, 200, { id: path.slice(1), name: 'CodeBasket WABA', currency: 'INR', timezone_id: '71', account_review_status: 'APPROVED', business_verification_status: 'verified', message_template_namespace: 'ns' });
    return json(res, 200, req.method === 'GET' ? { data: [] } : { success: true });
  });
}).listen(9900, () => console.log('meta stub on 9900'));
