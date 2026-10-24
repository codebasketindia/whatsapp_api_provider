package in.codebasket.cbwa.security;

import in.codebasket.cbwa.campaign.AudienceType;
import in.codebasket.cbwa.campaign.Campaign;
import in.codebasket.cbwa.campaign.CampaignRepository;
import in.codebasket.cbwa.campaign.CampaignStatus;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.contact.Contact;
import in.codebasket.cbwa.contact.ContactGroup;
import in.codebasket.cbwa.contact.ContactGroupRepository;
import in.codebasket.cbwa.contact.ContactRepository;
import in.codebasket.cbwa.contact.ContactSource;
import in.codebasket.cbwa.convtools.QrCode;
import in.codebasket.cbwa.convtools.QrCodeRepository;
import in.codebasket.cbwa.invoice.Invoice;
import in.codebasket.cbwa.invoice.InvoiceRepository;
import in.codebasket.cbwa.messaging.ChargeStatus;
import in.codebasket.cbwa.messaging.ConversationWindow;
import in.codebasket.cbwa.messaging.ConversationWindowRepository;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageDirection;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.messaging.MessageStatus;
import in.codebasket.cbwa.messaging.MessageType;
import in.codebasket.cbwa.meta.MetaSettings;
import in.codebasket.cbwa.meta.MetaSettingsRepository;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partnerhook.DeliveryStatus;
import in.codebasket.cbwa.partnerhook.PartnerWebhook;
import in.codebasket.cbwa.partnerhook.PartnerWebhookRepository;
import in.codebasket.cbwa.partnerhook.WebhookDeliveryLog;
import in.codebasket.cbwa.partnerhook.WebhookDeliveryLogRepository;
import in.codebasket.cbwa.payment.GatewayType;
import in.codebasket.cbwa.payment.PaymentOrder;
import in.codebasket.cbwa.payment.PaymentOrderRepository;
import in.codebasket.cbwa.payment.PaymentOrderStatus;
import in.codebasket.cbwa.publicapi.ApiKey;
import in.codebasket.cbwa.publicapi.ApiKeyRepository;
import in.codebasket.cbwa.publicapi.ApiKeyStatus;
import in.codebasket.cbwa.publicapi.ApiSecretHasher;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.template.ComponentType;
import in.codebasket.cbwa.template.ParameterFormat;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateComponent;
import in.codebasket.cbwa.template.TemplateComponentRepository;
import in.codebasket.cbwa.template.TemplateRepository;
import in.codebasket.cbwa.template.TemplateStatus;
import in.codebasket.cbwa.topup.PaymentMode;
import in.codebasket.cbwa.topup.TopupRequest;
import in.codebasket.cbwa.topup.TopupRequestRepository;
import in.codebasket.cbwa.topup.TopupStatus;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a full set of resources for two partners (A and B) so the torture test can address real ids,
 * ids of another tenant and non-existent ids for every route. Known secrets are planted so the
 * response-hygiene scan can prove they never leak.
 */
@Component
@RequiredArgsConstructor
public class TortureFixtures {

    public static final String APP_SECRET = "SECRET_app_9f8e7d";
    public static final String VERIFY_TOKEN = "SECRET_verify_5a4b3c";
    public static final String WABA_TOKEN = "SECRET_EAAwabatoken_77";
    public static final String PIN = "246813";
    public static final String WEBHOOK_SECRET = "whsec_SECRETwebhook0000000000000000";
    public static final String API_SECRET = "cbs_SECRETapi000000000000000000000000000000";

    private final TestData testData;
    private final CryptoService crypto;
    private final MetaSettingsRepository metaSettingsRepository;
    private final TemplateRepository templateRepository;
    private final TemplateComponentRepository componentRepository;
    private final MessageRepository messageRepository;
    private final ConversationWindowRepository windowRepository;
    private final ContactRepository contactRepository;
    private final ContactGroupRepository groupRepository;
    private final CampaignRepository campaignRepository;
    private final TopupRequestRepository topupRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiSecretHasher hasher;
    private final PartnerWebhookRepository webhookRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final QrCodeRepository qrCodeRepository;
    private final FileStorageService storage;
    private final in.codebasket.cbwa.sender.PhoneNumberRepository phoneNumberRepository;

    /** Ids of one partner's resources, keyed by a logical name used by the path resolver. */
    public static class Tenant {
        public Partner partner;
        public User user;
        public String token;
        public WhatsappAccount waba;
        public PhoneNumber sender;
        public final Map<String, String> ids = new LinkedHashMap<>();
        public final Set<String> markers = new java.util.HashSet<>();
        public String apiKey;
    }

    public static class World {
        public Tenant a;
        public Tenant b;
        public User superAdmin;
        public User support;
        public User finance;
        public String superToken;
        public String supportToken;
        public String financeToken;
        public final List<String> secrets = new ArrayList<>();
    }

    @Transactional
    public World build() {
        MetaSettings ms = metaSettingsRepository.findById(1L).orElseThrow();
        ms.setAppId("111222333");
        ms.setAppSecretEnc(crypto.encrypt(APP_SECRET));
        ms.setWebhookVerifyTokenEnc(crypto.encrypt(VERIFY_TOKEN));
        ms.setEmbeddedSignupConfigId("444555");
        metaSettingsRepository.save(ms);
        testData.price("IN", MessageCategory.UTILITY, "0.20");
        testData.price("IN", MessageCategory.MARKETING, "0.80");

        World w = new World();
        w.superAdmin = testData.superAdmin();
        w.support = testData.admin("SUPPORT");
        w.finance = testData.admin("FINANCE");
        w.superToken = testData.accessToken(w.superAdmin);
        w.supportToken = testData.accessToken(w.support);
        w.financeToken = testData.accessToken(w.finance);
        w.a = tenant("A", "Alpha Torture Traders");
        w.b = tenant("B", "Beta Torture Retail");
        w.secrets.addAll(List.of(APP_SECRET, VERIFY_TOKEN, WABA_TOKEN, PIN, WEBHOOK_SECRET, API_SECRET, TestData.PASSWORD));
        return w;
    }

    private Tenant tenant(String tag, String businessName) {
        Tenant t = new Tenant();
        t.partner = testData.partner(new BigDecimal("500"));
        t.partner.setBusinessName(businessName);
        t.token = testData.accessToken(t.partner);
        t.waba = testData.waba(WabaOwnerType.PARTNER, t.partner.getId(), BillingMode.TECH_PROVIDER);
        t.waba.setAccessTokenEnc(crypto.encrypt(WABA_TOKEN));
        t.sender = testData.sender(t.waba, t.partner.getId(), SenderType.PARTNER_OWN_SENDER);
        t.sender.setPinEnc(crypto.encrypt(PIN));
        phoneNumberRepository.save(t.sender);
        t.markers.add(businessName);
        t.ids.put("partner", t.partner.getId().toString());
        t.ids.put("waba", t.waba.getId().toString());
        t.ids.put("sender", t.sender.getId().toString());

        Template tpl = new Template();
        tpl.setUid(Ulids.next());
        tpl.setPartnerId(t.partner.getId());
        tpl.setWhatsappAccountId(t.waba.getId());
        tpl.setMetaTemplateId(String.valueOf(System.nanoTime()));
        tpl.setName("torture_" + tag.toLowerCase());
        tpl.setLanguage("en");
        tpl.setCategory(MessageCategory.UTILITY);
        tpl.setParameterFormat(ParameterFormat.POSITIONAL);
        tpl.setStatus(TemplateStatus.APPROVED);
        templateRepository.save(tpl);
        TemplateComponent c = new TemplateComponent();
        c.setTemplateId(tpl.getId());
        c.setPosition(1);
        c.setType(ComponentType.BODY);
        c.setText("Hello {{1}} from " + tag);
        componentRepository.save(c);
        t.ids.put("template", tpl.getId().toString());

        String waId = "9198" + (tag.equals("A") ? "11111111" : "22222222");
        Message m = new Message();
        m.setUid(Ulids.next());
        m.setPartnerId(t.partner.getId());
        m.setPhoneNumberId(t.sender.getId());
        m.setDirection(MessageDirection.OUTBOUND);
        m.setWaId(waId);
        m.setCountryCode("IN");
        m.setType(MessageType.TEMPLATE);
        m.setTemplateId(tpl.getId());
        m.setTemplateName(tpl.getName());
        m.setTemplateLanguage("en");
        m.setCategory(MessageCategory.UTILITY);
        m.setPreview("Hello marker-" + tag);
        m.setWamid("wamid.torture" + tag + System.nanoTime());
        m.setStatus(MessageStatus.DELIVERED);
        m.setPrice(new BigDecimal("0.2000"));
        m.setChargeStatus(ChargeStatus.NONE);
        m.setSource(MessageSource.DASHBOARD);
        messageRepository.save(m);
        t.ids.put("message", m.getUid());
        t.ids.put("waId", waId);
        t.markers.add(m.getUid());

        ConversationWindow cw = new ConversationWindow();
        cw.setPartnerId(t.partner.getId());
        cw.setPhoneNumberId(t.sender.getId());
        cw.setWaId(waId);
        cw.setLastInboundAt(Instant.now());
        cw.setExpiresAt(Instant.now().plusSeconds(3600));
        cw.setLastMessageAt(Instant.now());
        cw.setLastMessagePreview("hi");
        windowRepository.save(cw);

        Contact contact = new Contact();
        contact.setPartnerId(t.partner.getId());
        contact.setPhone(waId);
        contact.setName("Contact " + tag);
        contact.setSource(ContactSource.MANUAL);
        contact.setTags(new ArrayList<>(List.of("vip")));
        contact.setAttributes(new LinkedHashMap<>(Map.of("city", "Pune")));
        contactRepository.save(contact);
        t.ids.put("contact", contact.getId().toString());

        ContactGroup g = new ContactGroup();
        g.setPartnerId(t.partner.getId());
        g.setName("Group " + tag);
        groupRepository.save(g);
        t.ids.put("group", g.getId().toString());

        Campaign camp = new Campaign();
        camp.setUid(Ulids.next());
        camp.setPartnerId(t.partner.getId());
        camp.setName("Campaign " + tag);
        camp.setPhoneNumberId(t.sender.getId());
        camp.setTemplateId(tpl.getId());
        camp.setAudienceType(AudienceType.CONTACTS);
        camp.setAudience("{\"contactIds\":[" + contact.getId() + "]}");
        camp.setStatus(CampaignStatus.DRAFT);
        camp.setUnitPrice(MoneyUtil.ZERO);
        camp.setEstimatedCost(MoneyUtil.ZERO);
        camp.setChargedAmount(MoneyUtil.ZERO);
        camp.setRefundedAmount(MoneyUtil.ZERO);
        campaignRepository.save(camp);
        t.ids.put("campaign", camp.getId().toString());

        StoredFile proof = storage.store(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0},
                "proof.png", FileCategory.TOPUP_PROOF, t.partner.getId(), Set.of(), 1 << 20);
        t.ids.put("file", proof.getUid());

        TopupRequest tr = new TopupRequest();
        tr.setRequestNo("TRT" + tag + System.nanoTime() % 100000);
        tr.setPartnerId(t.partner.getId());
        tr.setAmount(new BigDecimal("100.0000"));
        tr.setPaymentMode(PaymentMode.UPI);
        tr.setUtr("UTRTORTURE" + tag + System.nanoTime() % 100000);
        tr.setPaymentDate(LocalDate.now());
        tr.setScreenshotFileId(proof.getId());
        tr.setStatus(TopupStatus.PENDING);
        topupRepository.save(tr);
        t.ids.put("topup", tr.getId().toString());

        PaymentOrder po = new PaymentOrder();
        po.setUid(Ulids.next());
        po.setPartnerId(t.partner.getId());
        po.setGateway(GatewayType.RAZORPAY);
        po.setGatewayOrderId("order_torture" + tag + System.nanoTime() % 100000);
        po.setBaseAmount(new BigDecimal("100"));
        po.setBonusAmount(MoneyUtil.ZERO);
        po.setGstPercent(BigDecimal.ZERO);
        po.setGstAmount(MoneyUtil.ZERO);
        po.setTotalAmount(new BigDecimal("100"));
        po.setCreditAmount(new BigDecimal("100"));
        po.setStatus(PaymentOrderStatus.CREATED);
        paymentOrderRepository.save(po);
        t.ids.put("payment", po.getUid());

        Invoice inv = new Invoice();
        inv.setInvoiceNo("CB/T/" + tag + System.nanoTime() % 100000);
        inv.setPartnerId(t.partner.getId());
        inv.setPaymentOrderId(po.getId());
        inv.setInvoiceDate(LocalDate.now());
        inv.setBillingName(t.partner.getBusinessName());
        inv.setTaxableAmount(new BigDecimal("100"));
        inv.setCgstAmount(MoneyUtil.ZERO);
        inv.setSgstAmount(MoneyUtil.ZERO);
        inv.setIgstAmount(MoneyUtil.ZERO);
        inv.setTotalAmount(new BigDecimal("100"));
        inv.setFileId(proof.getId());
        invoiceRepository.save(inv);
        t.ids.put("invoice", inv.getId().toString());

        ApiKey key = new ApiKey();
        key.setPartnerId(t.partner.getId());
        key.setName("torture");
        key.setApiKey("cbk_live_torture" + tag + Ulids.next().substring(0, 8).toLowerCase());
        key.setSecretHash(hasher.hash(API_SECRET));
        key.setSecretLast4("0000");
        key.setStatus(ApiKeyStatus.ACTIVE);
        apiKeyRepository.save(key);
        t.ids.put("apiKey", key.getId().toString());
        t.apiKey = key.getApiKey();

        PartnerWebhook hook = new PartnerWebhook();
        hook.setPartnerId(t.partner.getId());
        hook.setUrl("https://hooks.example.com/" + tag);
        hook.setSecretEnc(crypto.encrypt(WEBHOOK_SECRET));
        hook.setEvents(new ArrayList<>(List.of("message.status")));
        hook.setActive(true);
        webhookRepository.save(hook);
        WebhookDeliveryLog log = new WebhookDeliveryLog();
        log.setPartnerWebhookId(hook.getId());
        log.setPartnerId(t.partner.getId());
        log.setEventId(Ulids.next());
        log.setEventType("message.status");
        log.setPayload("{\"event\":\"message.status\"}");
        log.setStatus(DeliveryStatus.DEAD);
        log.setAttempt(5);
        deliveryLogRepository.save(log);
        t.ids.put("deliveryLog", log.getId().toString());

        QrCode qr = new QrCode();
        qr.setPartnerId(t.partner.getId());
        qr.setPhoneNumberId(t.sender.getId());
        qr.setMetaCode("QR" + tag + System.nanoTime() % 100000);
        qr.setPrefilledMessage("hi");
        qr.setDeepLinkUrl("https://wa.me/message/QR" + tag);
        qrCodeRepository.save(qr);
        t.ids.put("qr", qr.getId().toString());
        t.user = null;
        return t;
    }
}
