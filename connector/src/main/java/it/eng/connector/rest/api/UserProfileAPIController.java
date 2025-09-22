package it.eng.connector.rest.api;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.ChangePasswordRequest;
import it.eng.connector.dto.UpdateProfileRequest;
import it.eng.connector.dto.UserProfileResponse;
import it.eng.connector.service.UserService;
import it.eng.connector.util.ControllerUtils;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.PROFILE_V1)
@Slf4j
public class UserProfileAPIController {

    private final UserService userService;

    public UserProfileAPIController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get current user profile.
     * @param authentication the authentication object containing user principal
     * @return GenericApiResponse containing current user profile
     */
    @GetMapping
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> getCurrentUserProfile(Authentication authentication) {
        ApiUserPrincipal userPrincipal = (ApiUserPrincipal) authentication.getPrincipal();
        UserProfileResponse userProfile = userService.getCurrentUserProfile(userPrincipal);
        return ControllerUtils.success(userProfile, "User profile retrieved successfully");
    }

    /**
     * Update current user profile.
     * @param updateRequest the profile update request
     * @param authentication the authentication object containing user principal
     * @return GenericApiResponse containing updated user profile
     */
    @PutMapping
    public ResponseEntity<GenericApiResponse<UserProfileResponse>> updateCurrentUserProfile(
            @Valid @RequestBody UpdateProfileRequest updateRequest,
            Authentication authentication) {
        
        ApiUserPrincipal userPrincipal = (ApiUserPrincipal) authentication.getPrincipal();
        UserProfileResponse userProfile = userService.updateCurrentUserProfile(updateRequest, userPrincipal);
        return ControllerUtils.success(userProfile, "Profile updated successfully");
    }

    /**
     * Change password for current user.
     * @param changePasswordRequest the password change request
     * @param authentication the authentication object containing user principal
     * @return GenericApiResponse indicating password change success
     */
    @PutMapping("/password")
    public ResponseEntity<GenericApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest,
            Authentication authentication) {
        
        ApiUserPrincipal userPrincipal = (ApiUserPrincipal) authentication.getPrincipal();
        userService.changeCurrentUserPassword(changePasswordRequest, userPrincipal);
        return ControllerUtils.success("Password changed successfully");
    }

    /**
     * Delete current user account (soft delete).
     * @param authentication the authentication object containing user principal
     * @return GenericApiResponse indicating account deletion success
     */
    @DeleteMapping
    public ResponseEntity<GenericApiResponse<String>> deleteCurrentUser(Authentication authentication) {
        ApiUserPrincipal userPrincipal = (ApiUserPrincipal) authentication.getPrincipal();
        userService.deleteCurrentUser(userPrincipal);
        return ControllerUtils.success("Account deleted successfully");
    }
}
