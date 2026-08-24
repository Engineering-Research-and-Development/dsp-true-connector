package it.eng.datatransfer.properties;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.service.TenantContextHolder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provides data transfer configuration properties.
 * When a tenant context is active (via {@link TenantContextHolder}), tenant-specific
 * settings override the global application properties.
 */
@Component
public class DataTransferProperties {

	private final TenantRepository tenantRepository;

	@Value("${application.callback.address}")
	private String callbackAddress;

    /**
     *  Returns the maximum number of retry attempts for automatic transfer transitions.
     */
    @Getter
    @Value("${application.automatic.transfer.retry.max:3}")
	private int maxRetryAttempts;

    /**
     *  Returns the delay in milliseconds between automatic transfer retry attempts.
     */
    @Getter
    @Value("${application.automatic.transfer.retry.delay.ms:2000}")
	private long retryDelayMs;

	@Value("${application.automatic.transfer:false}")
	private boolean automaticTransfer;

	/**
	 * Constructs a new DataTransferProperties with the given tenant repository.
	 *
	 * @param tenantRepository repository used to look up per-tenant settings
	 */
	public DataTransferProperties(TenantRepository tenantRepository) {
		this.tenantRepository = tenantRepository;
	}

	/**
	 * Returns whether automatic data transfer is enabled.
	 * Uses the active tenant's setting when a tenant context is present,
	 * otherwise falls back to the global application property.
	 *
	 * @return {@code true} if automatic transfer is enabled
	 */
	public boolean isAutomaticTransfer() {
		return getActiveTenant()
				.map(Tenant::isAutomaticTransfer)
				.orElse(automaticTransfer);
	}

	/**
	 * Returns the provider callback address.
	 * Uses the active tenant's computed callback address when a tenant context is present,
	 * otherwise falls back to the global application property.
	 *
	 * @return provider callback base URL
	 */
	public String providerCallbackAddress() {
		return getActiveTenant()
				.map(t -> t.getCallbackAddress(callbackAddress))
				.orElse(callbackAddress);
	}

	/**
	 * Returns the consumer callback address, with trailing slash removed and
	 * {@code /consumer} suffix appended.
	 * Uses the active tenant's computed callback address when a tenant context is present,
	 * otherwise falls back to the global application property.
	 *
	 * @return consumer callback URL
	 */
	public String consumerCallbackAddress() {
		String base = getActiveTenant()
				.map(t -> t.getCallbackAddress(callbackAddress))
				.orElse(callbackAddress);
		String validated = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return validated + "/consumer";
	}

	/**
	 * Returns the raw application-level callback address, without tenant path.
	 *
	 * <p>Used when sending {@link it.eng.dataplane.api.message.DataFlowStartMessage} to a
	 * Data Plane microservice so that the DP can POST status callbacks back to
	 * {@code /api/v1/dataflows/complete} or {@code /api/v1/dataflows/error}. Those
	 * endpoints are on the admin (non-tenant) chain and must NOT include a tenant-path
	 * prefix. The tenant-aware {@link #providerCallbackAddress()} would return the
	 * tenant's address (e.g. {@code http://connector-a:8080/connector_a_tenant_id})
	 * which causes a 404 when the DP sends its callback.</p>
	 *
	 * @return the global {@code application.callback.address} value
	 */
	public String dataPlaneFeedbackAddress() {
		return callbackAddress;
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
