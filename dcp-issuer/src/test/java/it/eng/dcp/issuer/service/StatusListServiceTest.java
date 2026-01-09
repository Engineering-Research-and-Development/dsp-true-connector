package it.eng.dcp.issuer.service;

import it.eng.dcp.issuer.model.StatusList;
import it.eng.dcp.issuer.repository.StatusListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusListServiceTest {
    @Mock
    private StatusListRepository statusListRepository;

    @InjectMocks
    private StatusListService statusListService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void allocateStatusEntry_createsNewListIfNotExists() {
        String issuerDid = "did:web:issuer.example.com";
        String statusPurpose = "revocation";
        when(statusListRepository.findByIssuerDidAndStatusPurpose(issuerDid, statusPurpose)).thenReturn(Optional.empty());
        when(statusListRepository.save(any(StatusList.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StatusListService.StatusListEntryInfo entryInfo = statusListService.allocateStatusEntry(issuerDid, statusPurpose);
        assertNotNull(entryInfo);
        assertEquals(issuerDid + "/status/1", entryInfo.getStatusListId());
        assertEquals(0, entryInfo.getIndex());
    }

    @Test
    void updateStatus_throwsIfListNotFound() {
        when(statusListRepository.findById(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> statusListService.updateStatus("notfound", 1, true));
    }
}

