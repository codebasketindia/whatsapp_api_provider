package in.codebasket.cbwa.user;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.user.dto.UserDto;
import in.codebasket.cbwa.user.dto.UserRequests.CreateStaffRequest;
import in.codebasket.cbwa.user.dto.UserRequests.PermissionDto;
import in.codebasket.cbwa.user.dto.UserRequests.RoleDto;
import in.codebasket.cbwa.user.dto.UserRequests.RoleRequest;
import in.codebasket.cbwa.user.dto.UserRequests.StatusRequest;
import in.codebasket.cbwa.user.dto.UserRequests.UpdateStaffRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Staff & roles")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
public class StaffController {

    private final StaffService staffService;

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionDto>> permissions() {
        return ApiResponse.ok(staffService.permissions());
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleDto>> roles() {
        return ApiResponse.ok(staffService.roles());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleDto> createRole(@Valid @RequestBody RoleRequest req) {
        return ApiResponse.created(staffService.createRole(req), "Role created");
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleDto> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest req) {
        return ApiResponse.ok(staffService.updateRole(id, req), "Role updated");
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        staffService.deleteRole(id);
        return ApiResponse.ok(null, "Role deleted");
    }

    @GetMapping("/staff")
    public ApiResponse<PageResponse<UserDto>> staff(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(staffService.staff(search, status, page, size));
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto> create(@Valid @RequestBody CreateStaffRequest req) {
        return ApiResponse.created(staffService.createStaff(req), "Staff member added. Login details were emailed");
    }

    @PutMapping("/staff/{uid}")
    public ApiResponse<UserDto> update(@PathVariable String uid, @Valid @RequestBody UpdateStaffRequest req) {
        return ApiResponse.ok(staffService.updateStaff(uid, req), "Staff member updated");
    }

    @PatchMapping("/staff/{uid}/status")
    public ApiResponse<UserDto> status(@PathVariable String uid, @Valid @RequestBody StatusRequest req) {
        return ApiResponse.ok(staffService.setStatus(uid, req.status()), "Status updated");
    }

    @PostMapping("/staff/{uid}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable String uid) {
        staffService.resetPassword(uid);
        return ApiResponse.ok(null, "A temporary password was emailed");
    }
}
