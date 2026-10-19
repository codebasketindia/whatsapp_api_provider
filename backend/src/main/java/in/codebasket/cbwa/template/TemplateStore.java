package in.codebasket.cbwa.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.storage.StoredFileRepository;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.template.dto.TemplateDtos.ButtonDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.ComponentDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.PartnerRef;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.WabaRef;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Persists template components and maps templates to TemplateDto. */
@Component
@RequiredArgsConstructor
public class TemplateStore {

    private final TemplateComponentRepository componentRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final PartnerRepository partnerRepository;
    private final StoredFileRepository fileRepository;

    public void replaceComponents(Template t, List<ComponentDto> components) {
        componentRepository.deleteByTemplateId(t.getId());
        componentRepository.flush();
        List<TemplateComponent> rows = new ArrayList<>();
        int pos = 0;
        for (ComponentDto c : components) {
            TemplateComponent r = new TemplateComponent();
            r.setTemplateId(t.getId());
            r.setPosition(pos++);
            r.setType(ComponentType.valueOf(c.type()));
            r.setFormat(c.format());
            r.setText(c.text());
            r.setExample(c.example() == null ? null : c.example().toString());
            r.setButtons(c.buttons() == null ? null : JsonUtil.toJson(c.buttons()));
            r.setHeaderHandle(c.headerHandle());
            if (c.headerMediaFileUid() != null) {
                r.setHeaderMediaFileId(fileRepository.findByUid(c.headerMediaFileUid()).map(StoredFile::getId).orElse(null));
            }
            r.setAddSecurityRecommendation(c.addSecurityRecommendation());
            r.setCodeExpirationMinutes(c.codeExpirationMinutes());
            rows.add(r);
        }
        componentRepository.saveAll(rows);
    }

    public List<ComponentDto> components(Template t) {
        List<ComponentDto> out = new ArrayList<>();
        for (TemplateComponent r : componentRepository.findByTemplateIdOrderByPositionAsc(t.getId())) {
            JsonNode example = r.getExample() == null ? null : JsonUtil.readTree(r.getExample());
            List<ButtonDto> buttons = r.getButtons() == null ? null
                    : JsonUtil.fromJson(r.getButtons(), new TypeReference<List<ButtonDto>>() { });
            String fileUid = r.getHeaderMediaFileId() == null ? null
                    : fileRepository.findById(r.getHeaderMediaFileId()).map(StoredFile::getUid).orElse(null);
            out.add(new ComponentDto(r.getType().name(), r.getFormat(), r.getText(), example, buttons, fileUid,
                    r.getHeaderHandle(), r.getAddSecurityRecommendation(), r.getCodeExpirationMinutes()));
        }
        return out;
    }

    public TemplateDto toDto(Template t) {
        WabaRef waba = wabaRepository.findById(t.getWhatsappAccountId())
                .map(w -> new WabaRef(w.getId(), w.getWabaId(), w.getName())).orElse(null);
        PartnerRef partner = t.getPartnerId() == null ? null : partnerRepository.findById(t.getPartnerId())
                .map(p -> new PartnerRef(p.getId(), p.getBusinessName())).orElse(null);
        return new TemplateDto(t.getId(), t.getUid(), t.getName(), t.getLanguage(), t.getCategory().name(), t.getPreviousCategory(),
                t.getParameterFormat().name(), t.getStatus().name(), t.getRejectedReason(), t.getQualityScore(), waba, partner,
                t.getLibraryTemplateName(), components(t), t.getAdminReviewNote(), t.getMetaTemplateId(), t.getSubmittedAt(),
                t.getLastSyncedAt(), t.getCreatedAt(), t.getUpdatedAt());
    }

    /** Public URL for a stored header sample (used by the preview). */
    public static String fileUrl(String uid) {
        return uid == null ? null : "/api/files/" + uid;
    }
}
