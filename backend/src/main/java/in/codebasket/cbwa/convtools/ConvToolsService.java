package in.codebasket.cbwa.convtools;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.BlockResult;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.BlockedUserDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.CommandDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.ConversationalDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.FailedDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.QrDto;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.meta.MetaToolsApi;
import in.codebasket.cbwa.sender.PartnerPhoneNumber;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** T12-T21: QR codes & short links, welcome message / ice breakers / commands, block list (F5). */
@Service
@RequiredArgsConstructor
public class ConvToolsService {

    private final QrCodeRepository qrRepository;
    private final ConversationalComponentRepository convRepository;
    private final BlockedUserRepository blockedRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final MetaToolsApi toolsApi;
    private final MetaGraphClient graphClient;
    private final CryptoService crypto;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    // ---------------- QR codes ----------------

    public List<QrDto> listQr(PhoneNumber pn, String base) {
        return qrRepository.findByPhoneNumberIdOrderByIdDesc(pn.getId()).stream().map(q -> toDto(q, base)).toList();
    }

    public QrDto createQr(PhoneNumber pn, String message, String base) {
        JsonNode res = toolsApi.createQr(pn.getPhoneNumberId(), message.trim(), token(pn));
        return tx.execute(st -> {
            QrCode q = new QrCode();
            q.setPhoneNumberId(pn.getId());
            q.setPartnerId(partnerOf(pn));
            apply(q, res, pn, message.trim());
            qrRepository.save(q);
            auditService.log("QR_CREATE", "QR_CODE", q.getId(), null, Map.of("code", q.getMetaCode()), q.getPartnerId());
            return toDto(q, base);
        });
    }

    public QrDto updateQr(PhoneNumber pn, Long qrId, String message, String base) {
        QrCode existing = qrRepository.findByIdAndPhoneNumberId(qrId, pn.getId()).orElseThrow(() -> AppException.notFound("QR code"));
        JsonNode res = toolsApi.updateQr(pn.getPhoneNumberId(), existing.getMetaCode(), message.trim(), token(pn));
        return tx.execute(st -> {
            QrCode q = qrRepository.findById(qrId).orElseThrow();
            apply(q, res, pn, message.trim());
            qrRepository.save(q);
            return toDto(q, base);
        });
    }

    public void deleteQr(PhoneNumber pn, Long qrId) {
        QrCode q = qrRepository.findByIdAndPhoneNumberId(qrId, pn.getId()).orElseThrow(() -> AppException.notFound("QR code"));
        try {
            toolsApi.deleteQr(pn.getPhoneNumberId(), q.getMetaCode(), token(pn));
        } catch (MetaApiException e) {
            if (e.getHttpStatus() != 404 && e.getHttpStatus() != 400) {
                throw e;
            }
        }
        tx.executeWithoutResult(st -> {
            qrRepository.deleteById(qrId);
            auditService.log("QR_DELETE", "QR_CODE", qrId, Map.of("code", q.getMetaCode()), null, q.getPartnerId());
        });
    }

    /** T16: PNG proxied from Meta (or generated), SVG generated locally from the deep link. */
    public byte[] qrImage(PhoneNumber pn, Long qrId, String format) {
        QrCode q = qrRepository.findByIdAndPhoneNumberId(qrId, pn.getId()).orElseThrow(() -> AppException.notFound("QR code"));
        String link = q.getDeepLinkUrl() != null ? q.getDeepLinkUrl() : q.getShortLink();
        if ("svg".equalsIgnoreCase(format)) {
            return svg(link).getBytes(StandardCharsets.UTF_8);
        }
        if (q.getQrImagePngUrl() != null) {
            try {
                byte[] img = graphClient.downloadPublic(q.getQrImagePngUrl());
                if (img != null && img.length > 0) {
                    return img;
                }
            } catch (MetaApiException ignored) {
                // fall back to a locally generated image
            }
        }
        return png(link);
    }

    private void apply(QrCode q, JsonNode res, PhoneNumber pn, String message) {
        String code = res.path("code").asText(q.getMetaCode());
        if (code == null || code.isBlank()) {
            // Meta must return the QR code id; without it the code can never be updated or deleted
            throw new MetaApiException(502, null, null, "WhatsApp did not return a QR code. Please try again", null, null);
        }
        q.setMetaCode(code);
        q.setPrefilledMessage(res.path("prefilled_message").asText(message));
        q.setDeepLinkUrl(res.path("deep_link_url").asText(q.getDeepLinkUrl()));
        q.setQrImagePngUrl(res.path("qr_image_url").asText(q.getQrImagePngUrl()));
        q.setShortLink(shortLink(pn, message));
    }

    public static String shortLink(PhoneNumber pn, String message) {
        String number = pn.getWaNumber() != null ? pn.getWaNumber()
                : pn.getDisplayPhoneNumber() == null ? "" : pn.getDisplayPhoneNumber().replaceAll("[^0-9]", "");
        return "https://wa.me/" + number + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static QrDto toDto(QrCode q, String base) {
        String img = base + "/qr-codes/" + q.getId() + "/image";
        return new QrDto(q.getId(), q.getMetaCode(), q.getPrefilledMessage(), q.getDeepLinkUrl(), q.getShortLink(),
                img + "?format=png", img + "?format=svg", q.getCreatedAt());
    }

    private static BitMatrix matrix(String text) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512, hints);
        } catch (WriterException e) {
            throw AppException.badRequest("Could not create QR image");
        }
    }

    static byte[] png(String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix(text), "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw AppException.badRequest("Could not create QR image");
        }
    }

    static String svg(String text) {
        BitMatrix m = matrix(text);
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(m.getWidth()).append(' ').append(m.getHeight())
                .append("\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"");
        for (int y = 0; y < m.getHeight(); y++) {
            int x = 0;
            while (x < m.getWidth()) {
                if (m.get(x, y)) {
                    int start = x;
                    while (x < m.getWidth() && m.get(x, y)) {
                        x++;
                    }
                    sb.append('M').append(start).append(' ').append(y).append('h').append(x - start).append("v1h-").append(x - start).append('z');
                } else {
                    x++;
                }
            }
        }
        return sb.append("\"/></svg>").toString();
    }

    // ---------------- conversational components ----------------

    public ConversationalDto getConversational(PhoneNumber pn) {
        try {
            JsonNode ca = toolsApi.getConversationalAutomation(pn.getPhoneNumberId(), token(pn));
            if (!ca.isMissingNode() && !ca.isNull()) {
                List<String> prompts = new ArrayList<>();
                ca.path("prompts").forEach(p -> prompts.add(p.asText()));
                List<Map<String, String>> commands = new ArrayList<>();
                ca.path("commands").forEach(c -> commands.add(Map.of("name", c.path("command_name").asText(),
                        "description", c.path("command_description").asText())));
                boolean welcome = ca.path("enable_welcome_message").asBoolean(false);
                tx.executeWithoutResult(st -> saveConv(pn.getId(), welcome, prompts, commands, "SYNCED"));
            }
        } catch (MetaApiException ignored) {
            // show the locally stored copy
        }
        return convRepository.findByPhoneNumberId(pn.getId()).map(ConvToolsService::toDto)
                .orElse(new ConversationalDto(false, List.of(), List.of()));
    }

    public ConversationalDto setConversational(PhoneNumber pn, ConversationalDto req) {
        List<Map<String, String>> commands = req.commands().stream()
                .map(c -> Map.of("name", c.name().trim(), "description", c.description().trim())).toList();
        List<String> prompts = req.prompts().stream().map(String::trim).toList();
        toolsApi.setConversationalAutomation(pn.getPhoneNumberId(), req.enableWelcomeMessage(), prompts, commands, token(pn));
        return tx.execute(st -> {
            ConversationalComponent c = saveConv(pn.getId(), req.enableWelcomeMessage(), prompts, commands, "SYNCED");
            auditService.log("CONVERSATIONAL_UPDATE", "SENDER", pn.getId(), null, toDto(c), partnerOf(pn));
            return toDto(c);
        });
    }

    private ConversationalComponent saveConv(Long senderId, boolean welcome, List<String> prompts, List<Map<String, String>> commands,
                                             String status) {
        ConversationalComponent c = convRepository.findByPhoneNumberId(senderId).orElseGet(() -> {
            ConversationalComponent n = new ConversationalComponent();
            n.setPhoneNumberId(senderId);
            return n;
        });
        c.setEnableWelcomeMessage(welcome);
        c.setPrompts(new ArrayList<>(prompts));
        c.setCommands(new ArrayList<>(commands));
        c.setSyncStatus(status);
        c.setSyncedAt(Instant.now());
        return convRepository.save(c);
    }

    private static ConversationalDto toDto(ConversationalComponent c) {
        List<CommandDto> cmds = c.getCommands() == null ? List.of()
                : c.getCommands().stream().map(m -> new CommandDto(m.get("name"), m.get("description"))).toList();
        return new ConversationalDto(c.isEnableWelcomeMessage(), c.getPrompts() == null ? List.of() : c.getPrompts(), cmds);
    }

    // ---------------- block list ----------------

    public PageResponse<BlockedUserDto> blocked(PhoneNumber pn, Integer page, Integer size) {
        var p = Paging.of(page, size);
        return PageResponse.of(blockedRepository.findByPhoneNumberIdAndStatus(pn.getId(), BlockStatus.BLOCKED,
                        PageRequest.of(p.getPageNumber(), p.getPageSize(), Sort.by(Sort.Direction.DESC, "blockedAt"))),
                b -> new BlockedUserDto(b.getWaId(), b.getStatus().name(), b.getBlockedAt()));
    }

    /** Meta allows blocking only users who messaged in the last 24 hours. */
    public BlockResult block(PhoneNumber pn, List<String> waIds) {
        List<String> ids = normalise(waIds);
        JsonNode res = toolsApi.blockUsers(pn.getPhoneNumberId(), ids, token(pn));
        List<String> ok = new ArrayList<>();
        res.path("block_users").path("added_users").forEach(u -> ok.add(u.path("input").asText(u.path("wa_id").asText())));
        List<FailedDto> failed = failures(res);
        if (ok.isEmpty() && failed.isEmpty()) {
            ok.addAll(ids);
        }
        tx.executeWithoutResult(st -> {
            for (String w : ok) {
                BlockedUser b = blockedRepository.findByPhoneNumberIdAndWaId(pn.getId(), w).orElseGet(BlockedUser::new);
                b.setPhoneNumberId(pn.getId());
                b.setPartnerId(partnerOf(pn));
                b.setWaId(w);
                b.setStatus(BlockStatus.BLOCKED);
                b.setMetaError(null);
                b.setBlockedAt(Instant.now());
                b.setUnblockedAt(null);
                blockedRepository.save(b);
            }
            auditService.log("USERS_BLOCK", "SENDER", pn.getId(), null, Map.of("blocked", ok, "failed", failed.size()), partnerOf(pn));
        });
        return new BlockResult(ok, List.of(), failed);
    }

    public BlockResult unblock(PhoneNumber pn, List<String> waIds) {
        List<String> ids = normalise(waIds);
        JsonNode res = toolsApi.unblockUsers(pn.getPhoneNumberId(), ids, token(pn));
        List<String> ok = new ArrayList<>();
        res.path("block_users").path("removed_users").forEach(u -> ok.add(u.path("input").asText(u.path("wa_id").asText())));
        List<FailedDto> failed = failures(res);
        if (ok.isEmpty() && failed.isEmpty()) {
            ok.addAll(ids);
        }
        tx.executeWithoutResult(st -> {
            for (String w : ok) {
                blockedRepository.findByPhoneNumberIdAndWaId(pn.getId(), w).ifPresent(b -> {
                    b.setStatus(BlockStatus.UNBLOCKED);
                    b.setUnblockedAt(Instant.now());
                    blockedRepository.save(b);
                });
            }
            auditService.log("USERS_UNBLOCK", "SENDER", pn.getId(), null, Map.of("unblocked", ok), partnerOf(pn));
        });
        return new BlockResult(List.of(), ok, failed);
    }

    private static List<FailedDto> failures(JsonNode res) {
        List<FailedDto> failed = new ArrayList<>();
        res.path("block_users").path("failed_users").forEach(u -> {
            String reason = u.path("errors").isArray() && !u.path("errors").isEmpty()
                    ? u.path("errors").get(0).path("message").asText("Could not update") : "Could not update";
            if (reason.toLowerCase().contains("24")) {
                reason = "Only people who messaged you in the last 24 hours can be blocked";
            }
            failed.add(new FailedDto(u.path("input").asText(u.path("wa_id").asText()), reason));
        });
        return failed;
    }

    private static List<String> normalise(List<String> waIds) {
        return waIds.stream().map(w -> PhoneUtil.parse(w).map(PhoneUtil.Parsed::digits)
                .orElseThrow(() -> AppException.field("waIds", "Invalid number: " + w))).distinct().toList();
    }

    private String token(PhoneNumber pn) {
        return crypto.decrypt(wabaRepository.findById(pn.getWhatsappAccountId())
                .orElseThrow(() -> AppException.notFound("WhatsApp account")).getAccessTokenEnc());
    }

    private Long partnerOf(PhoneNumber pn) {
        return assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).map(PartnerPhoneNumber::getPartnerId).orElse(null);
    }
}
