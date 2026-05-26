package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@JsonDeserialize(builder = TransferProcess.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "transfer_process")
public class TransferProcess extends AbstractTransferMessage {

    @Serial
    private static final long serialVersionUID = -6329135422869881158L;

    @JsonIgnore
    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    @NotNull
    private String providerPid;

    @NotNull
    private TransferState state;

    /**
     * Used to store agreement so we can enforce it.
     */
    @JsonIgnore
    private String agreementId;
    @JsonIgnore
    private String callbackAddress;
    @JsonIgnore
    private String datasetId;

    /**
     * Determines which role the connector is for that contract negotiation (consumer or provider).
     */
    @JsonIgnore
    private String role;

    @JsonIgnore
    private DataAddress dataAddress;

    /**
     * Flag to check if the data is downloaded on consumer side.
     */
    @JsonIgnore
    private boolean isDownloaded;

    /**
     * Flag to indicate that a download is currently in progress on consumer side.
     * Set to {@code true} when download starts and reset to {@code false} when the
     * download finishes (success or failure). Visible in plain API responses so the
     * frontend can drive a download spinner.
     */
    @JsonIgnore
    private boolean isDownloadInProgress;

    /**
     * Id of the downloaded and stored data on consumer side.
     */
    @JsonIgnore
    private String dataId;

    @JsonIgnore
    private String format;

    @JsonIgnore
    @CreatedDate
    private Instant created;
    @JsonIgnore
    @CreatedBy
    private String createdBy;

    @JsonIgnore
    @LastModifiedDate
    private Instant modified;
    @JsonIgnore
    @LastModifiedBy
    private String lastModifiedBy;

    @JsonIgnore
    @Version
    @Field("version")
    private Long version;

    @JsonIgnore
    @Field("retryCount")
    private int retryCount;

    /**
     * Internal tracking state of the data flow (e.g. PREPARED, STARTED, COMPLETED, TERMINATED).
     * Not included in protocol JSON — used only for internal monitoring.
     */
    @JsonIgnore
    private String dataFlowState;

    /**
     * Last error message received from the data plane.
     * Not included in protocol JSON — used only for internal diagnostics.
     */
    @JsonIgnore
    private String dataFlowErrorMessage;

    /**
     * Internal transport profile chosen for this transfer process (e.g. {@code "stream:grpc"}).
     * Not included in protocol JSON — used only for internal Data Plane routing.
     */
    @JsonIgnore
    private String transportProfile;

    /**
     * Tenant this transfer process belongs to. Null for super-admin scope.
     */
    @JsonIgnore
    private String tenantId;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final TransferProcess message;

        private Builder() {
            message = new TransferProcess();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            message.id = id;
            return this;
        }

        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        public Builder state(TransferState state) {
            message.state = state;
            return this;
        }

        public Builder role(String role) {
            message.role = role;
            return this;
        }

        public Builder dataAddress(DataAddress dataAddress) {
            message.dataAddress = dataAddress;
            return this;
        }

        public Builder isDownloaded(boolean isDownloaded) {
            message.isDownloaded = isDownloaded;
            return this;
        }

        public Builder isDownloadInProgress(boolean isDownloadInProgress) {
            message.isDownloadInProgress = isDownloadInProgress;
            return this;
        }

        public Builder dataId(String dataId) {
            message.dataId = dataId;
            return this;
        }


        public Builder format(String format) {
            message.format = format;
            return this;
        }

        public Builder retryCount(int retryCount) {
            message.retryCount = retryCount;
            return this;
        }

        /**
         * Sets the internal data flow state (e.g. PREPARED, STARTED, COMPLETED, TERMINATED).
         *
         * @param dataFlowState the data flow state string
         * @return this builder
         */
        public Builder dataFlowState(String dataFlowState) {
            message.dataFlowState = dataFlowState;
            return this;
        }

        /**
         * Sets the last error message received from the data plane.
         *
         * @param dataFlowErrorMessage the data flow error message
         * @return this builder
         */
        public Builder dataFlowErrorMessage(String dataFlowErrorMessage) {
            message.dataFlowErrorMessage = dataFlowErrorMessage;
            return this;
        }

        public Builder tenantId(String tenantId) {
            message.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the internal transport profile for Data Plane routing.
         *
         * @param transportProfile the transport profile (e.g. {@code "stream:grpc"}), or {@code null} for standard HTTP
         * @return this builder
         */
        public Builder transportProfile(String transportProfile) {
            message.transportProfile = transportProfile;
            return this;
        }

        public Builder agreementId(String agreementId) {
            message.agreementId = agreementId;
            return this;
        }

        public Builder callbackAddress(String callbackAddress) {
            message.callbackAddress = callbackAddress;
            return this;
        }

        @JsonProperty("datasetId")
        public Builder datasetId(String datasetId) {
            message.datasetId = datasetId;
            return this;
        }

        // Auditable fields
        @JsonProperty("version")
        public Builder version(Long version) {
            message.version = version;
            return this;
        }

        @JsonProperty("createdBy")
        public Builder createdBy(String createdBy) {
            message.createdBy = createdBy;
            return this;
        }

        @JsonProperty("created")
        public Builder created(Instant created) {
            message.created = created;
            return this;
        }

        @JsonProperty("modified")
        public Builder modified(Instant modified) {
            message.modified = modified;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Builder lastModifiedBy(String lastModifiedBy) {
            message.lastModifiedBy = lastModifiedBy;
            return this;
        }

        public TransferProcess build() {
            if (message.id == null) {
                message.id = message.createNewPid();
            }
            if (message.consumerPid == null) {
                message.consumerPid = message.createNewPid();
            }
            if (message.providerPid == null) {
                message.providerPid = message.createNewPid();
            }
            Set<ConstraintViolation<TransferProcess>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException("TransferProcess - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @Override
    public String getType() {
        return TransferProcess.class.getSimpleName();
    }

    /**
     * Injects the tenant identifier into this transfer process.
     * Called after loading from DB or event, when tenant context is not available via TenantContextHolder.
     *
     * @param tenantId the tenant identifier to set
     */
    public void injectTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Create new TransferProcess from origin, with new TransferState.<br>
     * Used to update state when transition happens.
     *
     * @param newTransferState
     * @return new TransferProcess object from initial, with new state
     */
    public TransferProcess copyWithNewTransferState(TransferState newTransferState) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(newTransferState)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                // auditable fields
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified retryCount.
     * All other fields remain unchanged.
     *
     * @param retryCount the retry count value to persist
     * @return new TransferProcess instance with updated retryCount
     */
    public TransferProcess withRetryCount(int retryCount) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                // auditable fields
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified isDownloadInProgress flag.
     * All other fields remain unchanged.
     *
     * @param isDownloadInProgress {@code true} if a download is currently in progress, {@code false} otherwise
     * @return new TransferProcess instance with updated isDownloadInProgress flag
     */
    public TransferProcess withIsDownloadInProgress(boolean isDownloadInProgress) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                // auditable fields
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified isDownloaded flag.
     * All other fields remain unchanged.
     *
     * @param isDownloaded {@code true} if the artifact has been downloaded, {@code false} otherwise
     * @return new TransferProcess instance with updated isDownloaded flag
     */
    public TransferProcess withIsDownloaded(boolean isDownloaded) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified dataId.
     * All other fields remain unchanged.
     *
     * @param dataId the data identifier (e.g. S3 object key used to retrieve the downloaded artifact)
     * @return new TransferProcess instance with updated dataId
     */
    public TransferProcess withDataId(String dataId) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified internal dataplane state.
     * All other fields remain unchanged.
     *
     * @param dataFlowState the data flow state string (e.g. PREPARED, STARTED, COMPLETED, TERMINATED)
     * @return new TransferProcess instance with updated dataFlowState
     */
    public TransferProcess withDataFlowState(String dataFlowState) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified data plane error message.
     * All other fields remain unchanged.
     *
     * @param dataFlowErrorMessage the error message from the data plane
     * @return new TransferProcess instance with updated dataFlowErrorMessage
     */
    public TransferProcess withDataFlowErrorMessage(String dataFlowErrorMessage) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified data address.
     * All other fields remain unchanged.
     *
     * @param dataAddress the data address to set
     * @return new TransferProcess instance with updated dataAddress
     */
    public TransferProcess withDataAddress(DataAddress dataAddress) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(this.transportProfile)
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }

    /**
     * Creates a new TransferProcess with the specified transport profile.
     * All other fields remain unchanged.
     *
     * @param transportProfile the internal transport profile (e.g. {@code "stream:grpc"}), or {@code null}
     * @return new TransferProcess instance with updated transportProfile
     */
    public TransferProcess withTransportProfile(String transportProfile) {
        return TransferProcess.Builder.newInstance()
                .id(this.id)
                .agreementId(this.agreementId)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .dataAddress(this.dataAddress)
                .isDownloaded(this.isDownloaded)
                .isDownloadInProgress(this.isDownloadInProgress)
                .dataId(this.dataId)
                .format(this.format)
                .state(this.state)
                .role(this.role)
                .datasetId(this.datasetId)
                .retryCount(this.retryCount)
                .tenantId(this.tenantId)
                .dataFlowState(this.dataFlowState)
                .dataFlowErrorMessage(this.dataFlowErrorMessage)
                .transportProfile(transportProfile)
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .build();
    }
}
