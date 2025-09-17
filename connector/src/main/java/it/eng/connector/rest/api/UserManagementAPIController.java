package it.eng.connector.rest.api;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.AdminUserCreateRequest;
import it.eng.connector.dto.AdminUserUpdateRequest;
import it.eng.connector.dto.UserProfileResponse;
import it.eng.connector.model.User;
import it.eng.connector.service.UserService;
import it.eng.connector.util.ControllerUtils;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.USERS_V1)
@Slf4j
public class UserManagementAPIController {

    private final UserService userService;
    private final GenericFilterBuilder filterBuilder;
    private final PagedResourcesAssembler<User> pagedResourcesAssembler;
    private final PlainUserAssembler plainUserAssembler;

    public UserManagementAPIController(UserService userService,
                                       GenericFilterBuilder filterBuilder,
                                       PagedResourcesAssembler<User> pagedResourcesAssembler,
                                       PlainUserAssembler plainUserAssembler) {
        this.userService = userService;
        this.filterBuilder = filterBuilder;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainUserAssembler = plainUserAssembler;
    }

    /**
     * Get all users with dynamic filtering, pagination and sorting.
     * Supports filtering by any User field with automatic type conversion.
     * <p>
     * Examples:
     * - /api/v1/users?enabled=true&role=ROLE_ADMIN
     * - /api/v1/users?email=john@example.com
     * - /api/v1/users?firstName=John&page=0&size=10&sort=email,asc
     * - /api/v1/users?createdAt.from=2024-01-01&createdAt.to=2024-12-31
     *
     * @param request the HTTP request containing filter parameters
     * @param page    the page number for pagination (default is 0)
     * @param size    the size of each page for pagination (default is 20)
     * @param sort    the sorting criteria in the format "field,direction" (default is "email,asc")
     * @return ResponseEntity containing paginated users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedAPIResponse> getUsers(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "email,asc") String[] sort) {

        // Safely handle sort parameters to avoid ArrayIndexOutOfBoundsException
        Sort sorting = ControllerUtils.parseSortParameters(sort, "email", Sort.Direction.ASC);
        Pageable pageable = PageRequest.of(page, size, sorting);

        // Build filter map automatically from ALL request parameters
        Map<String, Object> filters = filterBuilder.buildFromRequest(request);
        log.debug("Generated filters: {}", filters);

        Page<User> users = userService.findUsers(filters, pageable);
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(users, plainUserAssembler);

        String filterString = filters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Users - Page " + page + " of " + users.getTotalPages() + ", Size: " + size +
                                ", Sort: " + sorting + ", Filters: [" + filterString + "]"));
    }

    /**
     * Get pending users (disabled users awaiting admin approval).
     *
     * @param page the page number for pagination (default is 0)
     * @param size the size of each page for pagination (default is 20)
     * @param sort the sorting criteria in the format "field,direction" (default is "createdDate,desc")
     * @return ResponseEntity containing paginated pending users
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedAPIResponse> getPendingUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate,desc") String[] sort) {

        // Safely handle sort parameters to avoid ArrayIndexOutOfBoundsException
        Sort sorting = ControllerUtils.parseSortParameters(sort, "createdDate", Sort.Direction.DESC);
        Pageable pageable = PageRequest.of(page, size, sorting);

        // Get pending users
        Page<User> pendingUsers = userService.getPendingUsers(pageable);

        // Convert to resources
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(pendingUsers, plainUserAssembler);

        log.info("Retrieved {} pending users", pendingUsers.getTotalElements());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Pending Users - Page " + page + " of " + pendingUsers.getTotalPages() +
                                ", Size: " + size + ", Sort: " + sorting));
    }

    /**
     * Get user by ID.
     *
     * @param id user ID
     * @return GenericApiResponse containing user profile
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> getUserById(@PathVariable String id) {
        User user = userService.getUserById(id);
        UserProfileResponse response = UserService.toUserProfileResponse(user);
        return ControllerUtils.success(response, String.format("User with id %s found", id));
    }

    /**
     * Create new user (admin only).
     *
     * @param createRequest user creation request
     * @return GenericApiResponse containing created user profile
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> createUser(
            @Valid @RequestBody AdminUserCreateRequest createRequest) {
        User user = userService.createUser(createRequest);
        UserProfileResponse response = UserService.toUserProfileResponse(user);
        return ControllerUtils.success(response, "User created successfully");
    }

    /**
     * Update user (admin only).
     *
     * @param id            user ID
     * @param updateRequest user update request
     * @return GenericApiResponse containing updated user profile
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody AdminUserUpdateRequest updateRequest) {
        User user = userService.updateUser(id, updateRequest);
        UserProfileResponse response = UserService.toUserProfileResponse(user);
        return ControllerUtils.success(response, "User updated successfully");
    }

    /**
     * Update user status (enable/disable).
     *
     * @param id      user ID
     * @param enabled new enabled status
     * @return GenericApiResponse containing updated user profile
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable String id,
            @RequestParam boolean enabled) {
        User user = userService.updateUserStatus(id, enabled);
        UserProfileResponse response = UserService.toUserProfileResponse(user);
        return ControllerUtils.success(response, "User status updated successfully");
    }

    /**
     * Reset user password (admin only).
     *
     * @param id          user ID
     * @param newPassword new password
     * @return GenericApiResponse confirming password reset
     */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericApiResponse<String>> resetUserPassword(
            @PathVariable String id,
            @RequestParam String newPassword) {
        userService.resetUserPassword(id, newPassword);
        return ControllerUtils.success("User password has been reset");
    }

    /**
     * Delete user account (admin only, soft delete).
     *
     * @param id             user ID
     * @param authentication current admin authentication
     * @return GenericApiResponse confirming deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericApiResponse<String>> deleteUser(
            @PathVariable String id,
            Authentication authentication) {

        // Get admin user ID from authentication
        ApiUserPrincipal adminPrincipal = (ApiUserPrincipal) authentication.getPrincipal();
        String adminUserId = adminPrincipal.getUserId();

        userService.deleteUser(id, adminUserId);
        return ControllerUtils.success("User account has been disabled");
    }
}