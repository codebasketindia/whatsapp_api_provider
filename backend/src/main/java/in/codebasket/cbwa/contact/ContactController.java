package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactIdsRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.GroupDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.GroupRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.IdsRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.ImportPreview;
import in.codebasket.cbwa.contact.dto.ContactDtos.ImportResult;
import in.codebasket.cbwa.contact.dto.ContactDtos.OptInRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.OptOutDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.OptOutRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.TagCount;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** C1-C11 and G1-G4 (partner). */
@Tag(name = "Partner - Contacts")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final ContactGroupService groupService;
    private final ContactImportService importService;

    @GetMapping("/contacts")
    public ApiResponse<PageResponse<ContactDto>> list(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) String tag,
                                                      @RequestParam(required = false) Long groupId,
                                                      @RequestParam(required = false) Boolean optedOut,
                                                      @RequestParam(required = false) Boolean blocked,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(contactService.list(pid(), search, tag, groupId, optedOut, blocked, page, size));
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContactDto> create(@Valid @RequestBody ContactRequest req) {
        return ApiResponse.created(contactService.create(pid(), req), "Contact added");
    }

    @GetMapping("/contacts/{id}")
    public ApiResponse<ContactDto> get(@PathVariable Long id) {
        return ApiResponse.ok(contactService.get(pid(), id));
    }

    @PutMapping("/contacts/{id}")
    public ApiResponse<ContactDto> update(@PathVariable Long id, @Valid @RequestBody ContactRequest req) {
        return ApiResponse.ok(contactService.update(pid(), id, req), "Contact updated");
    }

    @DeleteMapping("/contacts/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        contactService.delete(pid(), id);
        return ApiResponse.ok(null, "Contact deleted");
    }

    @PostMapping("/contacts/bulk-delete")
    public ApiResponse<Map<String, Integer>> bulkDelete(@Valid @RequestBody IdsRequest req) {
        return ApiResponse.ok(Map.of("deleted", contactService.bulkDelete(pid(), req.ids())), "Contacts deleted");
    }

    @PostMapping(value = "/contacts/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportPreview> importPreview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.preview(file));
    }

    /** C5: `mapping` is a JSON text field {phone, name, email, tags, attributes:{column: key}}. */
    @PostMapping(value = "/contacts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportResult> importContacts(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("mapping") String mapping,
                                                    @RequestParam(required = false) Long groupId,
                                                    @RequestParam(defaultValue = "IN") String defaultCountry,
                                                    @RequestParam(defaultValue = "false") boolean updateExisting) {
        ImportResult r = importService.importContacts(pid(), file, mapping, groupId, defaultCountry, updateExisting);
        return ApiResponse.ok(r, r.created() + " added, " + r.updated() + " updated, " + r.invalid() + " invalid");
    }

    @GetMapping("/contacts/import/sample")
    public ResponseEntity<byte[]> sample() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("contacts-sample.csv").build().toString())
                .contentType(new MediaType("text", "csv"))
                .body(importService.sampleCsv());
    }

    @GetMapping("/contacts/tags")
    public ApiResponse<List<TagCount>> tags() {
        return ApiResponse.ok(contactService.tags(pid()));
    }

    @PostMapping("/contacts/{id}/opt-out")
    public ApiResponse<ContactDto> optOut(@PathVariable Long id, @Valid @RequestBody(required = false) OptOutRequest req) {
        return ApiResponse.ok(contactService.optOut(pid(), id, req == null ? null : req.reason()), "Contact opted out");
    }

    @PostMapping("/contacts/{id}/opt-in")
    public ApiResponse<ContactDto> optIn(@PathVariable Long id, @RequestBody(required = false) OptInRequest req) {
        return ApiResponse.ok(contactService.optIn(pid(), id, req == null ? null : req.confirmConsent()), "Contact opted in");
    }

    @GetMapping("/opt-outs")
    public ApiResponse<PageResponse<OptOutDto>> optOuts(@RequestParam(required = false) String search,
                                                        @RequestParam(required = false) String source,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(contactService.optOuts(pid(), search, source, page, size));
    }

    @GetMapping("/contact-groups")
    public ApiResponse<PageResponse<GroupDto>> groups(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(groupService.list(pid(), search, page, size));
    }

    @GetMapping("/contact-groups/{id}")
    public ApiResponse<GroupDto> group(@PathVariable Long id) {
        return ApiResponse.ok(groupService.get(pid(), id));
    }

    @PostMapping("/contact-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDto> createGroup(@Valid @RequestBody GroupRequest req) {
        return ApiResponse.created(groupService.create(pid(), req), "Group created");
    }

    @PutMapping("/contact-groups/{id}")
    public ApiResponse<GroupDto> updateGroup(@PathVariable Long id, @Valid @RequestBody GroupRequest req) {
        return ApiResponse.ok(groupService.update(pid(), id, req), "Group updated");
    }

    @DeleteMapping("/contact-groups/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        groupService.delete(pid(), id);
        return ApiResponse.ok(null, "Group deleted");
    }

    @PostMapping("/contact-groups/{id}/members")
    public ApiResponse<Map<String, Integer>> addMembers(@PathVariable Long id, @Valid @RequestBody ContactIdsRequest req) {
        return ApiResponse.ok(Map.of("added", groupService.addMembers(pid(), id, req.contactIds())), "Contacts added to group");
    }

    @DeleteMapping("/contact-groups/{id}/members")
    public ApiResponse<Map<String, Integer>> removeMembers(@PathVariable Long id, @Valid @RequestBody ContactIdsRequest req) {
        return ApiResponse.ok(Map.of("removed", groupService.removeMembers(pid(), id, req.contactIds())), "Contacts removed from group");
    }

    private static Long pid() {
        return SecurityUtils.currentPartnerId();
    }
}
