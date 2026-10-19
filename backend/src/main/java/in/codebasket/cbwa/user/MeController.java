package in.codebasket.cbwa.user;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.AuthService;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.auth.dto.AuthDtos.ChangePasswordRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.TokenDto;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.Notification;
import in.codebasket.cbwa.common.notification.NotificationDto;
import in.codebasket.cbwa.common.notification.NotificationRepository;
import in.codebasket.cbwa.user.dto.UserDto;
import in.codebasket.cbwa.user.dto.UserRequests.TwoFactorToggleRequest;
import in.codebasket.cbwa.user.dto.UserRequests.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Tag(name = "Me")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final AuthService authService;
    private final NotificationRepository notificationRepository;

    @GetMapping
    public ApiResponse<UserDto> me() {
        return ApiResponse.ok(meService.me());
    }

    @PutMapping
    public ApiResponse<UserDto> update(@Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(meService.update(req), "Profile updated");
    }

    /** A10: returns new tokens because all previous sessions are revoked. */
    @PostMapping("/change-password")
    public ApiResponse<TokenDto> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        return ApiResponse.ok(authService.changePassword(req.currentPassword(), req.newPassword()), "Password changed");
    }

    @PutMapping("/two-factor")
    public ApiResponse<UserDto> twoFactor(@Valid @RequestBody TwoFactorToggleRequest req) {
        return ApiResponse.ok(meService.toggleTwoFactor(req.enabled(), req.password()),
                req.enabled() ? "Two-step login turned on" : "Two-step login turned off");
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationDto>> notifications(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        AuthPrincipal p = SecurityUtils.current();
        return ApiResponse.ok(PageResponse.of(notificationRepository.findForUser(p.userId(), p.partnerId(), unreadOnly,
                Paging.of(page, size)), NotificationDto::of));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        AuthPrincipal p = SecurityUtils.current();
        return ApiResponse.ok(Map.of("count", notificationRepository.countUnread(p.userId(), p.partnerId())));
    }

    @Transactional
    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        AuthPrincipal p = SecurityUtils.current();
        Notification n = notificationRepository.findById(id).orElseThrow(() -> AppException.notFound("Notification"));
        boolean mine = p.partnerId() != null
                ? p.partnerId().equals(n.getPartnerId()) && "PARTNER".equals(n.getAudience())
                : "ADMIN".equals(n.getAudience()) && (n.getUserId() == null || n.getUserId().equals(p.userId()));
        if (!mine) {
            throw AppException.notFound("Notification");
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        }
        return ApiResponse.ok(null);
    }

    @Transactional
    @PostMapping("/notifications/read-all")
    public ApiResponse<Map<String, Integer>> readAll() {
        AuthPrincipal p = SecurityUtils.current();
        return ApiResponse.ok(Map.of("updated", notificationRepository.markAllRead(p.userId(), p.partnerId(), Instant.now())));
    }
}
