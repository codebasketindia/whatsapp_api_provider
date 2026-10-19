package in.codebasket.cbwa.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.messaging.InboundMedia;
import in.codebasket.cbwa.messaging.InboundMediaRepository;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Downloads inbound media to our storage (Meta media URLs expire after a few minutes and need the
 * WABA token): GET /{MEDIA_ID} -> url -> download -> stored_files (MEDIA_INBOUND).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboundMediaJob {

    private static final int MAX_ATTEMPTS = 5;

    private final InboundMediaRepository mediaRepository;
    private final MessageRepository messageRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final SenderAccessService senderAccess;
    private final MetaGraphClient graph;
    private final FileStorageService storage;

    @Scheduled(fixedDelay = 5000, initialDelay = 15_000)
    public void run() {
        List<InboundMedia> pending = mediaRepository.findTop50ByDownloadStatusAndAttemptsLessThanOrderByIdAsc("PENDING", MAX_ATTEMPTS);
        pending.forEach(this::download);
    }

    public void download(InboundMedia im) {
        try {
            Message m = messageRepository.findById(im.getMessageId()).orElseThrow();
            PhoneNumber pn = phoneNumberRepository.findById(m.getPhoneNumberId()).orElseThrow();
            String token = senderAccess.accessToken(pn);
            JsonNode info = graph.get("/" + im.getMediaId(), Map.of("phone_number_id", pn.getPhoneNumberId()), token);
            String url = info.path("url").asText(null);
            if (url == null) {
                throw new IllegalStateException("Media URL missing");
            }
            byte[] bytes = graph.download(url, token);
            String name = im.getFilename() != null ? im.getFilename() : "media-" + im.getMediaId();
            StoredFile f = storage.store(bytes, name, FileCategory.MEDIA_INBOUND, im.getPartnerId(), null, 100L << 20);
            im.setFileId(f.getId());
            im.setFileSize((long) bytes.length);
            if (im.getMimeType() == null) {
                im.setMimeType(f.getMimeType());
            }
            im.setDownloadStatus("DONE");
        } catch (RuntimeException e) {
            im.setAttempts(im.getAttempts() + 1);
            if (im.getAttempts() >= MAX_ATTEMPTS) {
                im.setDownloadStatus("FAILED");
            }
            log.info("Inbound media {} download failed (attempt {}): {}", im.getMediaId(), im.getAttempts(), e.getMessage());
        }
        mediaRepository.save(im);
    }
}
