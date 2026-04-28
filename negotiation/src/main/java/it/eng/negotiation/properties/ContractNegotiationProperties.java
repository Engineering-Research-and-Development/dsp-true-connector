package it.eng.negotiation.properties;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.service.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provides contract negotiation configuration properties.
 * When a tenant context is active (via {@link TenantContextHolder}), tenant-specific
 * settings override the global application properties.
 */
@Component
public class ContractNegotiationProperties {

	private final TenantRepository tenantRepository;

	@Value("${application.callback.address}")
	private String callbackAddress;
	
	@Value("${application.automatic.negotiation}")
	private boolean automaticNegotiation;

	@Value("${application.automatic.negotiation.retry.max:3}")
	private int maxRetries;

	@Value("${application.automatic.negotiation.retry.delay.ms:2000}")
	private long retryDelayMs;

	@Value("${server.port}")
	private String serverPort;

	/**
	 * Constructs a new ContractNegotiationProperties with the given tenant repository.
	 *
	 * @param tenantRepository repository used to look up per-tenant settings
	 */
	public ContractNegotiationProperties(TenantRepository tenantRepository) {
		this.tenantRepository = tenantRepository;
	}

	/**
	 * Returns the connector identifier.
	 * Uses the active tenant's connectorId when a tenant context is present,
	 * otherwise falls back to {@code "connectorId"}.
	 *
	 * @return connector identifier string
	 */
	public String connectorId() {
		return getActiveTenant()
				.map(Tenant::getConnectorId)
				.orElse("connectorId");
	}

	/**
	 * Returns whether automatic negotiation is enabled.
	 * Uses the active tenant's setting when a tenant context is present,
	 * otherwise falls back to the global application property.
	 *
	 * @return {@code true} if automatic negotiation is enabled
	 */
	public boolean isAutomaticNegotiation() {
		return getActiveTenant()
				.map(Tenant::isAutomaticNegotiation)
				.orElse(automaticNegotiation);
	}

	/**
	 * Returns the maximum number of <em>retries</em> for automatic negotiation transitions.
	 * The total number of attempts is {@code maxRetries + 1}: one initial attempt plus up to
	 * {@code maxRetries} retries. Setting this to {@code 0} means no retries — a single
	 * failure goes straight to {@code TERMINATED}.
	 *
	 * @return maximum number of retries before transitioning to TERMINATED
	 */
	public int getMaxRetries() {
		return maxRetries;
	}

	/**
	 * Returns the delay in milliseconds between automatic negotiation retry attempts.
	 *
	 * @return retry delay in milliseconds
	 */
	public long getRetryDelayMs() {
		return retryDelayMs;
	}

	/**
	 * Returns the provider callback address.
	 * Uses the active tenant's callbackAddress when a tenant context is present,
	 * otherwise falls back to the global application property.
	 *
	 * @return provider callback base URL
	 */
	public String providerCallbackAddress() {
		return getActiveTenant()
				.map(Tenant::getCallbackAddress)
				.orElse(callbackAddress);
	}

	/**
	 * Returns the consumer callback address, with trailing slash removed and
	 * {@code /consumer} suffix appended.
	 * Uses the active tenant's callbackAddress when a tenant context is present,
	 * otherwise falls back to the global application property.
	 *
	 * @return consumer callback URL
	 */
	public String consumerCallbackAddress() {
		String base = getActiveTenant()
				.map(Tenant::getCallbackAddress)
				.orElse(callbackAddress);
		String validated = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return validated + "/consumer";
	}

	/**
	 * Returns the server port.
	 *
	 * @return server port string
	 */
	public String serverPort() {
		return serverPort;
	}

	/**
	 * Returns the assignee identifier string.
	 *
	 * @return assignee name
	 */
	public String getAssignee() {
		return "TRUEConnector v2";
	}

	/**
	 * Resolves the active (enabled) tenant from the current thread's tenant context.
	 * Returns an empty Optional when no tenant context is set or the tenant is disabled.
	 *
	 * @return Optional containing the active Tenant, or empty if none
	 */
	private Optional<Tenant> getActiveTenant() {
		return Optional.ofNullable(TenantContextHolder.getTenantId())
				.flatMap(id -> tenantRepository.findById(id).filter(Tenant::isEnabled));
	}
}
