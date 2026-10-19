package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.contact.ContactService;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactRequest;
import in.codebasket.cbwa.messaging.MessageQueryService;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.Balance;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.BulkRequest;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.BulkResult;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.PublicSender;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.SendResult;
import in.codebasket.cbwa.template.TemplateService;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PU1-PU9: Partner Public API. Auth: X-API-KEY + X-API-SECRET (ApiKeyAuthFilter). */
@Tag(name = "Partner Public API")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicApiController {

    private final PublicApiService publicApi;
    private final MessageQueryService messageQuery;
    private final TemplateService templateService;
    private final ContactService contactService;

    @Operation(summary = "Send a message (text, media, interactive, template)")
    @PostMapping("/messages/send")
    public ApiResponse<SendResult> send(@Valid @RequestBody SendMessageRequest req,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        AuthPrincipal p = SecurityUtils.current();
        return ApiResponse.ok(publicApi.send(p.partnerId(), p.apiKeyId(), req, idempotencyKey), "Message queued");
    }

    @Operation(summary = "Send one template to up to 1000 recipients")
    @PostMapping("/messages/bulk")
    public ApiResponse<BulkResult> bulk(@Valid @RequestBody BulkRequest req) {
        AuthPrincipal p = SecurityUtils.current();
        BulkResult r = publicApi.bulk(p.partnerId(), p.apiKeyId(), req);
        return ApiResponse.ok(r, r.accepted().size() + " queued, " + r.rejected().size() + " rejected");
    }

    @Operation(summary = "Get a message and its status")
    @GetMapping("/messages/{id}")
    public ApiResponse<MessageDto> message(@PathVariable String id) {
        return ApiResponse.ok(messageQuery.detail(id, SecurityUtils.currentPartnerId(), false));
    }

    @Operation(summary = "List templates")
    @GetMapping("/templates")
    public ApiResponse<PageResponse<TemplateDto>> templates(@RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String language,
                                                            @RequestParam(required = false) String search,
                                                            @RequestParam(required = false) Long senderId,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(templateService.partnerList(SecurityUtils.currentPartnerId(), null, senderId, status, null,
                language, search, page, size));
    }

    @Operation(summary = "Create a template (submitted to Meta / admin review)")
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateDto> createTemplate(@Valid @RequestBody TemplateRequest req,
                                                   @RequestParam(required = false) String from) {
        Long partnerId = SecurityUtils.currentPartnerId();
        TemplateRequest effective = req;
        if (req.wabaDbId() == null && req.senderId() == null) {
            Long senderId = publicApi.resolveSender(partnerId, null, from);
            effective = new TemplateRequest(null, senderId, req.name(), req.language(), req.category(), req.parameterFormat(),
                    req.components(), req.allowCategoryChange(), true, null);
        }
        return ApiResponse.created(templateService.create(partnerId, effective), "Template created");
    }

    @Operation(summary = "List sender numbers")
    @GetMapping("/senders")
    public ApiResponse<List<PublicSender>> senders() {
        return ApiResponse.ok(publicApi.senders(SecurityUtils.currentPartnerId()));
    }

    @Operation(summary = "Wallet balance")
    @GetMapping("/wallet/balance")
    public ApiResponse<Balance> balance() {
        return ApiResponse.ok(publicApi.balance(SecurityUtils.currentPartnerId()));
    }

    @Operation(summary = "List contacts")
    @GetMapping("/contacts")
    public ApiResponse<PageResponse<ContactDto>> contacts(@RequestParam(required = false) String search,
                                                          @RequestParam(required = false) String tag,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(contactService.list(SecurityUtils.currentPartnerId(), search, tag, null, null, null, page, size));
    }

    @Operation(summary = "Create a contact")
    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContactDto> createContact(@Valid @RequestBody ContactRequest req) {
        return ApiResponse.created(contactService.create(SecurityUtils.currentPartnerId(), req), "Contact created");
    }
}
