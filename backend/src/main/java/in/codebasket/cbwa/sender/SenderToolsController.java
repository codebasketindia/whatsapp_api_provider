package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.convtools.ConvToolsService;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.BlockResult;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.BlockedUserDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.ConversationalDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.QrDto;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.QrRequest;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.WaIdsRequest;
import in.codebasket.cbwa.sender.SenderScope.Access;
import in.codebasket.cbwa.sender.dto.SenderDtos.AnalyticsDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.BusinessProfileDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.HealthDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.PinRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.RequestCodeRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.VerifyCodeRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * T1-T21: one controller serving both /api/admin/senders/{id}/... and /api/partner/senders/{id}/...
 * URL security limits each prefix to its role; SenderScope applies permissions and ownership.
 */
@Tag(name = "Sender tools")
@RestController
@RequiredArgsConstructor
public class SenderToolsController {

    private static final String A = "/api/admin/senders/{id}";
    private static final String P = "/api/partner/senders/{id}";

    private final SenderScope scope;
    private final SenderToolsService tools;
    private final ConvToolsService convTools;

    @PostMapping({A + "/sync", P + "/sync"})
    public ApiResponse<SenderDto> sync(@PathVariable Long id) {
        return ApiResponse.ok(tools.sync(scope.resolve(id, Access.WRITE)), "Synced with WhatsApp");
    }

    @PostMapping({A + "/request-code", P + "/request-code"})
    public ApiResponse<Map<String, Boolean>> requestCode(@PathVariable Long id, @Valid @RequestBody RequestCodeRequest req) {
        tools.requestCode(scope.resolve(id, Access.OWNER), req.method(), req.language());
        return ApiResponse.ok(Map.of("sent", true), "Verification code sent by " + req.method().toLowerCase());
    }

    @PostMapping({A + "/verify-code", P + "/verify-code"})
    public ApiResponse<SenderDto> verifyCode(@PathVariable Long id, @Valid @RequestBody VerifyCodeRequest req) {
        return ApiResponse.ok(tools.verifyCode(scope.resolve(id, Access.OWNER), req.code()), "Number verified");
    }

    @PostMapping({A + "/register", P + "/register"})
    public ApiResponse<SenderDto> register(@PathVariable Long id, @Valid @RequestBody PinRequest req) {
        return ApiResponse.ok(tools.register(scope.resolve(id, Access.OWNER), req.pin()), "Number registered");
    }

    @PostMapping({A + "/deregister", P + "/deregister"})
    public ApiResponse<SenderDto> deregister(@PathVariable Long id) {
        return ApiResponse.ok(tools.deregister(scope.resolve(id, Access.OWNER)), "Number deregistered");
    }

    @PutMapping({A + "/two-step-pin", P + "/two-step-pin"})
    public ApiResponse<Void> pin(@PathVariable Long id, @Valid @RequestBody PinRequest req) {
        tools.changePin(scope.resolve(id, Access.OWNER), req.pin());
        return ApiResponse.ok(null, "PIN changed");
    }

    @GetMapping({A + "/profile", P + "/profile"})
    public ApiResponse<BusinessProfileDto> profile(@PathVariable Long id) {
        return ApiResponse.ok(tools.getProfile(scope.resolve(id, Access.READ)));
    }

    @PutMapping({A + "/profile", P + "/profile"})
    public ApiResponse<BusinessProfileDto> updateProfile(@PathVariable Long id, @Valid @RequestBody BusinessProfileDto req) {
        return ApiResponse.ok(tools.updateProfile(scope.resolve(id, Access.WRITE), req), "Business profile updated");
    }

    @PostMapping(value = {A + "/profile/photo", P + "/profile/photo"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BusinessProfileDto> photo(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(tools.uploadPhoto(scope.resolve(id, Access.WRITE), file), "Profile photo updated");
    }

    @GetMapping({A + "/health", P + "/health"})
    public ApiResponse<HealthDto> health(@PathVariable Long id) {
        return ApiResponse.ok(tools.health(scope.resolve(id, Access.READ)));
    }

    @GetMapping({A + "/analytics", P + "/analytics"})
    public ApiResponse<AnalyticsDto> analytics(@PathVariable Long id,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) String granularity) {
        return ApiResponse.ok(tools.analytics(scope.resolve(id, Access.READ), from, to));
    }

    // ---------------- QR codes ----------------

    @GetMapping({A + "/qr-codes", P + "/qr-codes"})
    public ApiResponse<List<QrDto>> qrList(@PathVariable Long id) {
        return ApiResponse.ok(convTools.listQr(scope.resolve(id, Access.READ), base(id)));
    }

    @PostMapping({A + "/qr-codes", P + "/qr-codes"})
    public ApiResponse<QrDto> qrCreate(@PathVariable Long id, @Valid @RequestBody QrRequest req) {
        return ApiResponse.ok(convTools.createQr(scope.resolve(id, Access.WRITE), req.prefilledMessage(), base(id)), "QR code created");
    }

    @PutMapping({A + "/qr-codes/{qrId}", P + "/qr-codes/{qrId}"})
    public ApiResponse<QrDto> qrUpdate(@PathVariable Long id, @PathVariable Long qrId, @Valid @RequestBody QrRequest req) {
        return ApiResponse.ok(convTools.updateQr(scope.resolve(id, Access.WRITE), qrId, req.prefilledMessage(), base(id)), "QR code updated");
    }

    @DeleteMapping({A + "/qr-codes/{qrId}", P + "/qr-codes/{qrId}"})
    public ApiResponse<Void> qrDelete(@PathVariable Long id, @PathVariable Long qrId) {
        convTools.deleteQr(scope.resolve(id, Access.WRITE), qrId);
        return ApiResponse.ok(null, "QR code deleted");
    }

    @GetMapping({A + "/qr-codes/{qrId}/image", P + "/qr-codes/{qrId}/image"})
    public ResponseEntity<byte[]> qrImage(@PathVariable Long id, @PathVariable Long qrId,
                                          @RequestParam(defaultValue = "png") String format) {
        byte[] img = convTools.qrImage(scope.resolve(id, Access.READ), qrId, format);
        boolean svg = "svg".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .contentType(svg ? MediaType.valueOf("image/svg+xml") : MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"whatsapp-qr-" + qrId + (svg ? ".svg" : ".png") + "\"")
                .body(img);
    }

    // ---------------- conversational components ----------------

    @GetMapping({A + "/conversational-components", P + "/conversational-components"})
    public ApiResponse<ConversationalDto> conv(@PathVariable Long id) {
        return ApiResponse.ok(convTools.getConversational(scope.resolve(id, Access.READ)));
    }

    @PutMapping({A + "/conversational-components", P + "/conversational-components"})
    public ApiResponse<ConversationalDto> setConv(@PathVariable Long id, @Valid @RequestBody ConversationalDto req) {
        return ApiResponse.ok(convTools.setConversational(scope.resolve(id, Access.WRITE), req), "Welcome message and ice breakers saved");
    }

    // ---------------- block list ----------------

    @GetMapping({A + "/blocked-users", P + "/blocked-users"})
    public ApiResponse<PageResponse<BlockedUserDto>> blocked(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(convTools.blocked(scope.resolve(id, Access.READ), page, size));
    }

    @PostMapping({A + "/blocked-users", P + "/blocked-users"})
    public ApiResponse<BlockResult> block(@PathVariable Long id, @Valid @RequestBody WaIdsRequest req) {
        return ApiResponse.ok(convTools.block(scope.resolve(id, Access.WRITE), req.waIds()), "Block list updated");
    }

    @DeleteMapping({A + "/blocked-users", P + "/blocked-users"})
    public ApiResponse<BlockResult> unblock(@PathVariable Long id, @Valid @RequestBody WaIdsRequest req) {
        return ApiResponse.ok(convTools.unblock(scope.resolve(id, Access.WRITE), req.waIds()), "Block list updated");
    }

    private static String base(Long id) {
        return (SecurityUtils.current().isAdmin() ? "/api/admin/senders/" : "/api/partner/senders/") + id;
    }
}
