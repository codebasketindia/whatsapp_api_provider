package in.codebasket.cbwa.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.storage.StoredFileRepository;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.HeaderVariable;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.Media;
import in.codebasket.cbwa.messaging.lookup.MsgTemplateComponentRepository;
import in.codebasket.cbwa.messaging.lookup.MsgTemplateRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates a {@link SendMessageRequest} and turns it into the Meta /messages request body.
 * Media given as an uploaded file is stored as {"_fileUid": uid}; the queue worker uploads it to
 * Meta just before sending and replaces it with {"id": mediaId}.
 */
@Component
@RequiredArgsConstructor
public class MessagePayloadBuilder {

    public static final String FILE_UID_KEY = "_fileUid";

    private final MsgTemplateRepository templateRepository;
    private final MsgTemplateComponentRepository componentRepository;
    private final StoredFileRepository storedFileRepository;
    private final MessageRepository messageRepository;

    public record Built(ObjectNode payload, MessageType type, String preview, Template template, MessageCategory category,
                        String contextWamid) {
    }

    public Built build(Long partnerId, PhoneNumber sender, String to, SendMessageRequest req) {
        String type = req.type();
        ObjectNode p = JsonUtil.MAPPER.createObjectNode();
        p.put("messaging_product", "whatsapp");
        p.put("recipient_type", "individual");
        p.put("to", to);
        p.put("type", type);

        String contextWamid = resolveContext(partnerId, req);
        if (contextWamid != null) {
            p.putObject("context").put("message_id", contextWamid);
        }
        String preview;
        Template template = null;
        MessageCategory category = MessageCategory.SERVICE;
        MessageType mt;
        switch (type) {
            case "text" -> {
                if (req.text() == null || blank(req.text().body())) {
                    throw AppException.field("text.body", "Message text is required");
                }
                if (req.text().body().length() > 4096) {
                    throw AppException.field("text.body", "Message text can be at most 4096 characters");
                }
                ObjectNode t = p.putObject("text").put("body", req.text().body());
                if (Boolean.TRUE.equals(req.text().previewUrl())) {
                    t.put("preview_url", true);
                }
                preview = req.text().body();
                mt = MessageType.TEXT;
            }
            case "image", "video", "audio", "document", "sticker" -> {
                Media m = req.media();
                if (m == null || (blank(m.fileUid()) && blank(m.metaMediaId()) && blank(m.link()))) {
                    throw AppException.field("media", "Upload a file or give a media link");
                }
                ObjectNode media = mediaObject(partnerId, m.fileUid(), m.metaMediaId(), m.link());
                if (!blank(m.caption()) && !type.equals("audio") && !type.equals("sticker")) {
                    media.put("caption", m.caption());
                }
                if (type.equals("document") && !blank(m.filename())) {
                    media.put("filename", m.filename());
                }
                p.set(type, media);
                preview = !blank(m.caption()) ? m.caption() : "[" + type + "]";
                mt = MessageType.valueOf(type.toUpperCase());
            }
            case "location" -> {
                var l = req.location();
                if (l == null || l.latitude() == null || l.longitude() == null) {
                    throw AppException.field("location", "Latitude and longitude are required");
                }
                if (Math.abs(l.latitude()) > 90 || Math.abs(l.longitude()) > 180) {
                    throw AppException.field("location", "Latitude/longitude out of range");
                }
                ObjectNode loc = p.putObject("location").put("latitude", l.latitude()).put("longitude", l.longitude());
                if (!blank(l.name())) {
                    loc.put("name", l.name());
                }
                if (!blank(l.address())) {
                    loc.put("address", l.address());
                }
                preview = "[location] " + (blank(l.name()) ? l.latitude() + "," + l.longitude() : l.name());
                mt = MessageType.LOCATION;
            }
            case "contacts" -> {
                if (req.contacts() == null || req.contacts().isEmpty()) {
                    throw AppException.field("contacts", "At least one contact is required");
                }
                for (JsonNode c : req.contacts()) {
                    if (blank(c.path("name").path("formatted_name").asText(null))) {
                        throw AppException.field("contacts", "Each contact needs name.formatted_name");
                    }
                }
                p.set("contacts", JsonUtil.MAPPER.valueToTree(req.contacts()));
                preview = "[contact] " + req.contacts().get(0).path("name").path("formatted_name").asText();
                mt = MessageType.CONTACTS;
            }
            case "reaction" -> {
                var r = req.reaction();
                if (r == null) {
                    throw AppException.field("reaction", "Reaction details are required");
                }
                String target = r.messageWamid();
                if (blank(target) && !blank(r.messageUid())) {
                    target = messageRepository.findByUidAndPartnerId(r.messageUid(), partnerId).map(Message::getWamid).orElse(null);
                }
                if (blank(target)) {
                    throw AppException.field("reaction.messageUid", "Message to react to was not found");
                }
                p.putObject("reaction").put("message_id", target).put("emoji", r.emoji() == null ? "" : r.emoji());
                preview = blank(r.emoji()) ? "[reaction removed]" : r.emoji();
                mt = MessageType.REACTION;
            }
            case "interactive" -> {
                preview = validateInteractive(req.interactive());
                p.set("interactive", req.interactive());
                mt = MessageType.INTERACTIVE;
            }
            case "template" -> {
                if (req.template() == null) {
                    throw AppException.field("template", "Template details are required");
                }
                template = resolveTemplate(partnerId, sender, req.template());
                TemplateMessageBuilder.Result r = TemplateMessageBuilder.build(template,
                        componentRepository.findByTemplateIdOrderByPositionAsc(template.getId()), req.template(),
                        hv -> headerMedia(partnerId, hv));
                p.set("template", r.template());
                preview = r.preview();
                category = template.getCategory();
                mt = MessageType.TEMPLATE;
            }
            default -> throw AppException.field("type", "Unsupported message type");
        }
        return new Built(p, mt, truncate(preview), template, category, contextWamid);
    }

    /** Resolves only the template (used for pricing estimates) - no variable validation. */
    public Template resolveTemplate(Long partnerId, PhoneNumber sender, SendMessageRequest.TemplateRef ref) {
        Template t;
        if (ref.templateId() != null) {
            t = templateRepository.findById(ref.templateId()).orElseThrow(() -> AppException.notFound("Template"));
        } else {
            if (blank(ref.name())) {
                throw AppException.field("template.name", "Template name or id is required");
            }
            if (!blank(ref.language())) {
                t = templateRepository.findByWhatsappAccountIdAndNameAndLanguage(sender.getWhatsappAccountId(), ref.name(), ref.language())
                        .orElseThrow(() -> AppException.notFound("Template"));
            } else {
                List<Template> list = templateRepository.findByWhatsappAccountIdAndName(sender.getWhatsappAccountId(), ref.name());
                t = list.stream().filter(x -> x.getStatus() == TemplateStatus.APPROVED).findFirst()
                        .or(() -> list.stream().findFirst())
                        .orElseThrow(() -> AppException.notFound("Template"));
            }
        }
        if (!t.getWhatsappAccountId().equals(sender.getWhatsappAccountId())
                || (t.getPartnerId() != null && !t.getPartnerId().equals(partnerId))) {
            throw AppException.notFound("Template");
        }
        if (t.getStatus() != TemplateStatus.APPROVED) {
            String why = switch (t.getStatus()) {
                case PAUSED -> "This template is paused by Meta due to low quality";
                case DISABLED -> "This template is disabled by Meta";
                case REJECTED, INTERNAL_REJECTED -> "This template was rejected";
                case PENDING, PENDING_REVIEW -> "This template is still waiting for approval";
                default -> "This template is not approved";
            };
            throw new AppException(ErrorCode.TEMPLATE_NOT_APPROVED, why);
        }
        return t;
    }

    private ObjectNode headerMedia(Long partnerId, HeaderVariable hv) {
        return mediaObject(partnerId, hv.fileUid(), hv.metaMediaId(), hv.link());
    }

    private ObjectNode mediaObject(Long partnerId, String fileUid, String metaMediaId, String link) {
        ObjectNode media = JsonUtil.MAPPER.createObjectNode();
        if (!blank(metaMediaId)) {
            media.put("id", metaMediaId);
        } else if (!blank(fileUid)) {
            StoredFile f = storedFileRepository.findByUid(fileUid)
                    .filter(sf -> partnerId.equals(sf.getPartnerId()))
                    .orElseThrow(() -> AppException.field("media.fileUid", "Uploaded file not found"));
            media.put(FILE_UID_KEY, f.getUid());
        } else {
            if (!link.toLowerCase().startsWith("https://") || link.length() > 2000) {
                throw AppException.field("media.link", "Media link must be a public https:// URL");
            }
            media.put("link", link);
        }
        return media;
    }

    private String resolveContext(Long partnerId, SendMessageRequest req) {
        if (!blank(req.replyToWamid())) {
            return req.replyToWamid();
        }
        if (!blank(req.replyToUid())) {
            return messageRepository.findByUidAndPartnerId(req.replyToUid(), partnerId).map(Message::getWamid)
                    .orElseThrow(() -> AppException.field("replyToUid", "Message to reply to was not found"));
        }
        return null;
    }

    static String validateInteractive(JsonNode i) {
        if (i == null || !i.isObject()) {
            throw AppException.field("interactive", "Interactive message details are required");
        }
        String itype = i.path("type").asText("");
        String body = i.path("body").path("text").asText(null);
        switch (itype) {
            case "button" -> {
                JsonNode buttons = i.path("action").path("buttons");
                if (!buttons.isArray() || buttons.isEmpty() || buttons.size() > 3) {
                    throw AppException.field("interactive.action.buttons", "Use 1 to 3 reply buttons");
                }
                for (JsonNode b : buttons) {
                    String title = b.path("reply").path("title").asText("");
                    if (title.isBlank() || title.length() > 20 || b.path("reply").path("id").asText("").isBlank()) {
                        throw AppException.field("interactive.action.buttons", "Each button needs an id and a title (max 20 characters)");
                    }
                }
                requireBody(body);
            }
            case "list" -> {
                JsonNode sections = i.path("action").path("sections");
                if (!sections.isArray() || sections.isEmpty() || sections.size() > 10) {
                    throw AppException.field("interactive.action.sections", "Use 1 to 10 sections");
                }
                int rows = 0;
                for (JsonNode s : sections) {
                    rows += s.path("rows").size();
                }
                if (rows == 0 || rows > 10) {
                    throw AppException.field("interactive.action.sections", "A list can have 1 to 10 rows in total");
                }
                if (i.path("action").path("button").asText("").isBlank()) {
                    throw AppException.field("interactive.action.button", "List button text is required");
                }
                requireBody(body);
            }
            case "cta_url" -> {
                JsonNode params = i.path("action").path("parameters");
                if (!"cta_url".equals(i.path("action").path("name").asText()) || params.path("url").asText("").isBlank()
                        || params.path("display_text").asText("").isBlank()) {
                    throw AppException.field("interactive.action", "CTA URL needs display_text and url");
                }
                requireBody(body);
            }
            case "location_request_message" -> {
                if (!"send_location".equals(i.path("action").path("name").asText())) {
                    throw AppException.field("interactive.action.name", "Location request action name must be send_location");
                }
                requireBody(body);
            }
            case "flow", "product", "product_list" -> {
                // passed through to Meta as given
            }
            default -> throw AppException.field("interactive.type", "Unsupported interactive type");
        }
        return body == null ? "[" + itype + "]" : body;
    }

    private static void requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw AppException.field("interactive.body.text", "Body text is required");
        }
        if (body.length() > 1024) {
            throw AppException.field("interactive.body.text", "Body text can be at most 1024 characters");
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
