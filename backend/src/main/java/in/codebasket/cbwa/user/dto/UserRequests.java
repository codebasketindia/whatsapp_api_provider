package in.codebasket.cbwa.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class UserRequests {

    private UserRequests() {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String name,
                                       @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$", message = "Enter a valid mobile number") String mobile,
                                       @Pattern(regexp = "LIGHT|DARK|SYSTEM", message = "Invalid theme") String theme) {
    }

    public record TwoFactorToggleRequest(@NotNull Boolean enabled, @NotBlank String password) {
    }

    public record RoleRequest(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "Use capital letters, digits and _") String code,
                              @NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
                              @NotNull List<String> permissions) {
    }

    public record CreateStaffRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 190) String email,
                                     @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$", message = "Enter a valid mobile number") String mobile,
                                     @NotNull Long roleId, @Size(min = 8, max = 72) String password) {
    }

    public record UpdateStaffRequest(@NotBlank @Size(max = 120) String name,
                                     @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$", message = "Enter a valid mobile number") String mobile,
                                     @NotNull Long roleId) {
    }

    public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {
    }

    public record RoleDto(Long id, String code, String name, String description, boolean isSystem, String userType,
                          List<String> permissions, long userCount) {
    }

    public record PermissionDto(String code, String module, String description) {
    }
}
