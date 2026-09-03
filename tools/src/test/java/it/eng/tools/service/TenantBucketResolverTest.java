package it.eng.tools.service;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantBucketResolverTest {

    private static final String TENANT_ID = "engineering";
    private static final String TENANT_BUCKET = "tenant-bucket";
    private static final String GLOBAL_BUCKET = "global-bucket";

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private TenantBucketResolver resolver;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // -----------------------------------------------------------------------
    // resolveBucketName() -- reads from TenantContextHolder
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolveBucketName() returns tenant bucket when context is set and tenant has bucket")
    void resolveBucketName_contextSet_tenantHasBucket() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithBucket(TENANT_BUCKET)));

        String result = resolver.resolveBucketName();

        assertEquals(TENANT_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName() falls back to global bucket when no tenant context")
    void resolveBucketName_noContext_fallsBackToGlobal() {
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName();

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName() falls back to global bucket when tenant context is blank")
    void resolveBucketName_blankContext_fallsBackToGlobal() {
        TenantContextHolder.setTenantId("  ");
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName();

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName() falls back to global bucket when tenant has no bucket configured")
    void resolveBucketName_contextSet_tenantNoBucket_fallsBackToGlobal() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithBucket(null)));
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName();

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName() falls back to global bucket when tenant is not found")
    void resolveBucketName_contextSet_tenantNotFound_fallsBackToGlobal() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName();

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName() throws when no tenant context and global bucket is blank")
    void resolveBucketName_noContext_globalBucketBlank_throws() {
        when(s3Properties.getBucketName()).thenReturn("");

        assertThrows(IllegalStateException.class, () -> resolver.resolveBucketName());
    }

    @Test
    @DisplayName("resolveBucketName() throws when no tenant context and global bucket is null")
    void resolveBucketName_noContext_globalBucketNull_throws() {
        when(s3Properties.getBucketName()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> resolver.resolveBucketName());
    }

    // -----------------------------------------------------------------------
    // resolveBucketName(String tenantId) -- explicit tenant ID overload
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolveBucketName(tenantId) returns tenant bucket when tenant has bucket")
    void resolveBucketNameExplicit_tenantHasBucket() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithBucket(TENANT_BUCKET)));

        String result = resolver.resolveBucketName(TENANT_ID);

        assertEquals(TENANT_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName(tenantId) falls back to global when tenant has no bucket")
    void resolveBucketNameExplicit_tenantNoBucket_fallsBackToGlobal() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithBucket(null)));
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName(TENANT_ID);

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName(tenantId) falls back to global when tenant has empty bucket name")
    void resolveBucketNameExplicit_tenantEmptyBucket_fallsBackToGlobal() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithBucket("  ")));
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName(TENANT_ID);

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName(tenantId) falls back to global when tenant is not found")
    void resolveBucketNameExplicit_tenantNotFound_fallsBackToGlobal() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName(TENANT_ID);

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName(null) falls back to global bucket")
    void resolveBucketNameExplicit_nullTenantId_fallsBackToGlobal() {
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName((String) null);

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName(blank) falls back to global bucket")
    void resolveBucketNameExplicit_blankTenantId_fallsBackToGlobal() {
        when(s3Properties.getBucketName()).thenReturn(GLOBAL_BUCKET);

        String result = resolver.resolveBucketName("  ");

        assertEquals(GLOBAL_BUCKET, result);
    }

    @Test
    @DisplayName("resolveBucketName(tenantId) throws when tenant not found and global bucket is blank")
    void resolveBucketNameExplicit_tenantNotFound_globalBlank_throws() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(s3Properties.getBucketName()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> resolver.resolveBucketName(TENANT_ID));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private Tenant tenantWithBucket(String bucketName) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .participantId("urn:connector:engineering")
                .enabled(true)
                .bucketName(bucketName)
                .build();
    }
}
