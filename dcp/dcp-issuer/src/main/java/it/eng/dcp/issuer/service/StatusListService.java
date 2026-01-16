package it.eng.dcp.issuer.service;

import it.eng.dcp.issuer.model.StatusList;
import it.eng.dcp.issuer.repository.StatusListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.BitSet;

/**
 * Service for managing status lists and entries for verifiable credentials.
 */
@Service
public class StatusListService {
    private static final int DEFAULT_STATUS_LIST_SIZE = 100000; // Can be made configurable

    private final StatusListRepository statusListRepository;

    @Autowired
    public StatusListService(StatusListRepository statusListRepository) {
        this.statusListRepository = statusListRepository;
    }

    /**
     * Allocates a new status entry (bit) in the status list for the given issuer and purpose.
     * @param issuerDid The issuer's DID
     * @param statusPurpose The status purpose (e.g., "revocation")
     * @return StatusListEntryInfo containing status list URI and index
     */
    @Transactional
    public StatusListEntryInfo allocateStatusEntry(String issuerDid, String statusPurpose) {
        StatusList statusList = statusListRepository.findByIssuerDidAndStatusPurpose(issuerDid, statusPurpose)
                .orElseGet(() -> {
                    String id = issuerDid + "/status/1";
                    return StatusList.Builder.newInstance()
                            .id(id)
                            .issuerDid(issuerDid)
                            .statusPurpose(statusPurpose)
                            .size(DEFAULT_STATUS_LIST_SIZE)
                            .build();
                });
        BitSet bits = statusList.getBitSet();
        int index = bits.nextClearBit(0);
        bits.set(index);
        statusList.setBitSet(bits);
        statusListRepository.save(statusList);
        return new StatusListEntryInfo(statusList.getId(), index);
    }

    /**
     * Updates the status (e.g., revoke/suspend) of a credential in the status list.
     * @param statusListId The status list resource ID
     * @param index The bit index to update
     * @param revoked True to revoke, false to activate
     */
    @Transactional
    public void updateStatus(String statusListId, int index, boolean revoked) {
        StatusList statusList = statusListRepository.findById(statusListId)
                .orElseThrow(() -> new IllegalArgumentException("Status list not found: " + statusListId));
        BitSet bits = statusList.getBitSet();
        bits.set(index, revoked);
        statusList.setBitSet(bits);
        statusListRepository.save(statusList);
    }

    /**
     * Retrieves the status list by ID.
     * @param statusListId The status list resource ID
     * @return The StatusList object
     */
    public StatusList getStatusList(String statusListId) {
        return statusListRepository.findById(statusListId)
                .orElseThrow(() -> new IllegalArgumentException("Status list not found: " + statusListId));
    }

    /**
     * Publishes the status list (e.g., makes it available at a URI).
     * @param statusListId The status list resource ID
     */
    public void publishStatusList(String statusListId) {
        // TODO: Implement publication logic (e.g., expose via REST or static file)
    }

    /**
     * Info about a status list entry for embedding in credentials.
     */
    public static class StatusListEntryInfo {
        private final String statusListId;
        private final int index;
        public StatusListEntryInfo(String statusListId, int index) {
            this.statusListId = statusListId;
            this.index = index;
        }
        public String getStatusListId() { return statusListId; }
        public int getIndex() { return index; }
    }
}
