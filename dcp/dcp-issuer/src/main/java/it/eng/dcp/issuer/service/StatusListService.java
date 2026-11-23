package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.config.IssuerProperties;
import it.eng.dcp.issuer.model.StatusList;
import it.eng.dcp.issuer.repository.StatusListRepository;
import it.eng.dcp.issuer.service.jwt.StatusListCredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Allocates, revokes and publishes status list entries of issued credentials (DCP v1.0.1 §6.10).
 *
 * <p>Each profile has its own lists: StatusList2021 for {@code vc11-sl2021/jwt} and BitstringStatusList for
 * {@code vc20-bssl/jwt}. A new entry keeps its status bit at 0 (valid); allocation is tracked by a counter, so
 * indices are never reused. Revoking sets the bit to 1 and re-signs the list. The signed list is served without
 * authentication at {@code <baseUrl>/issuer/status/<listId>}.
 *
 * <p>Concurrent updates of a list are detected with optimistic locking and retried.
 */
@Service
@Slf4j
public class StatusListService {

    /** The status purpose of revocation lists. */
    public static final String REVOCATION_PURPOSE = "revocation";

    /** The path, below the Issuer base URL, under which status list credentials are served. */
    public static final String STATUS_PATH = "/issuer/status/";

    /** 131072 entries (16 KB), the minimum size the status list specifications set for group privacy. */
    static final int DEFAULT_STATUS_LIST_SIZE = 131_072;

    private static final int MAX_ATTEMPTS = 5;

    private final StatusListRepository statusListRepository;
    private final IssuerProperties issuerProperties;
    private final StatusListCredentialGenerator credentialGenerator;

    /**
     * Creates the service.
     *
     * @param statusListRepository the status list repository
     * @param issuerProperties the Issuer configuration, which provides the base URL of the status list URLs
     * @param credentialGenerator signs status list credentials
     * @throws IllegalStateException if {@code dcp.base-url} is not set, because status list URLs would not resolve
     */
    public StatusListService(StatusListRepository statusListRepository,
                             IssuerProperties issuerProperties,
                             StatusListCredentialGenerator credentialGenerator) {
        String baseUrl = issuerProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("dcp.base-url must be set to publish status lists");
        }
        this.statusListRepository = statusListRepository;
        this.issuerProperties = issuerProperties;
        this.credentialGenerator = credentialGenerator;
    }

    /**
     * Allocates a new status entry for a credential of the given profile. The entry's status bit stays 0, so the
     * credential is valid until it is revoked. A new list is created when there is none or the current one is full.
     *
     * @param issuerDid the issuer DID
     * @param statusPurpose the status purpose, for example {@code revocation}
     * @param profile the credential profile, which selects the status list format
     * @return the status list credential URL and the allocated index
     * @throws IllegalStateException if the list stays contended
     */
    public StatusListEntry allocateStatusEntry(String issuerDid, String statusPurpose, ProfileId profile) {
        Objects.requireNonNull(issuerDid, "issuerDid");
        Objects.requireNonNull(statusPurpose, "statusPurpose");
        Objects.requireNonNull(profile, "profile");
        return withRetry(() -> {
            StatusList statusList = statusListRepository
                    .findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(issuerDid, statusPurpose, profile)
                    .filter(list -> !list.isFull())
                    .orElseGet(() -> newStatusList(issuerDid, statusPurpose, profile));
            int index = statusList.allocateIndex();
            StatusList saved = statusListRepository.save(statusList);
            log.debug("Allocated status list entry {} in list {}", index, saved.getId());
            return new StatusListEntry(saved.getUrl(), index);
        });
    }

    /**
     * Revokes an allocated entry: sets its status bit to 1 and re-signs the status list credential.
     * Revocation is not reversible; revoking an entry that is already revoked changes nothing.
     *
     * @param statusListId the list ID
     * @param index the status list index
     * @return true when the entry was revoked by this call, false when it was already revoked
     * @throws NoSuchElementException if the list does not exist
     * @throws IllegalArgumentException if the index has not been allocated
     */
    public boolean revoke(String statusListId, int index) {
        return withRetry(() -> {
            StatusList statusList = findList(statusListId)
                    .orElseThrow(() -> new NoSuchElementException("Status list not found: " + statusListId));
            if (!statusList.isAllocated(index)) {
                throw new IllegalArgumentException("Status list index " + index + " is not allocated in list "
                        + statusListId);
            }
            if (statusList.isSet(index)) {
                return false;
            }
            statusList.setBit(index);
            statusList.setSignedCredential(credentialGenerator.generate(statusList));
            statusListRepository.save(statusList);
            log.info("Revoked status list entry {} in list {}", index, statusListId);
            return true;
        });
    }

    /**
     * Returns the signed status list credential to publish. The credential is re-signed when it is missing or was
     * signed with a key that is no longer the Issuer's current signing key.
     *
     * @param statusListId the list ID
     * @return the published credential, or empty if the list does not exist
     */
    public Optional<PublishedStatusList> publishStatusList(String statusListId) {
        return withRetry(() -> findList(statusListId).map(statusList -> {
            StatusList published = statusList;
            if (!credentialGenerator.isSignedWithCurrentKey(statusList.getSignedCredential())) {
                statusList.setSignedCredential(credentialGenerator.generate(statusList));
                published = statusListRepository.save(statusList);
            }
            return new PublishedStatusList(published.getSignedCredential(),
                    StatusListCredentialGenerator.mediaTypeOf(published.getProfile()));
        }));
    }

    private Optional<StatusList> findList(String statusListId) {
        if (statusListId == null || statusListId.isBlank()) {
            return Optional.empty();
        }
        return statusListRepository.findById(statusListId);
    }

    private StatusList newStatusList(String issuerDid, String statusPurpose, ProfileId profile) {
        String listId = UUID.randomUUID().toString();
        StatusList statusList = StatusList.Builder.newInstance()
                .id(listId)
                .url(statusListUrl(listId))
                .issuerDid(issuerDid)
                .statusPurpose(statusPurpose)
                .profile(profile)
                .size(DEFAULT_STATUS_LIST_SIZE)
                .build();
        statusList.setSignedCredential(credentialGenerator.generate(statusList));
        log.info("Created {} status list {} for purpose {}", profile.getStatusListType(), listId, statusPurpose);
        return statusList;
    }

    private String statusListUrl(String listId) {
        String baseUrl = issuerProperties.getBaseUrl();
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + STATUS_PATH + listId;
    }

    private static <T> T withRetry(Supplier<T> action) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException e) {
                last = e;
                log.debug("Concurrent status list update, retrying (attempt {} of {})", attempt, MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Status list update failed after " + MAX_ATTEMPTS + " attempts", last);
    }

    /**
     * A status list entry to embed in a credential's {@code credentialStatus}.
     *
     * @param statusListCredential the URL of the status list credential
     * @param index the status list index
     */
    public record StatusListEntry(String statusListCredential, int index) {
    }

    /**
     * A signed status list credential ready to be served.
     *
     * @param credential the compact JWT
     * @param mediaType the media type to serve it with
     */
    public record PublishedStatusList(String credential, String mediaType) {
    }
}
