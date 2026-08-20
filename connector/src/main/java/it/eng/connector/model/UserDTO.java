package it.eng.connector.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for user create and update operations.
 *
 * <p>When creating a non-SUPER_ADMIN user, {@code tenantId} must reference an existing,
 * enabled tenant.  SUPER_ADMIN users are exempt and may omit {@code tenantId}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

	private String firstName;
	private String lastName;
	private String email;
	private String password;
	private String newPassword;
	private Role role;
	/** The tenant this user belongs to.  Optional only for the {@code SUPER_ADMIN} role. */
	private String tenantId;
	private boolean enabled;
	private boolean expired;
	private boolean locked;
}
