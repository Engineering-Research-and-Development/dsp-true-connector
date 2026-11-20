package it.eng.tools.model;

/**
 * Public tenant information containing only its name and realm.
 *
 * @param name the tenant name
 * @param realm the tenant realm
 */
public record PublicTenantInfo(String name, String realm) {
}
