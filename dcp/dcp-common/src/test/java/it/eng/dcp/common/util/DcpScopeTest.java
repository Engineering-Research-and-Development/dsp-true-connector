package it.eng.dcp.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DcpScopeTest {

    @Test
    void parse_typeAlias() {
        DcpScope s = DcpScope.parse("org.eclipse.dspace.dcp.vc.type:MembershipCredential");
        assertFalse(s.isUnqualified());
        assertFalse(s.isIdAlias());
        assertTrue(s.isStandardAlias());
        assertEquals("MembershipCredential", s.discriminator());
        assertFalse(s.hasAction());
    }

    @Test
    void parse_idAlias() {
        DcpScope s = DcpScope.parse("org.eclipse.dspace.dcp.vc.id:urn:uuid:1234");
        assertTrue(s.isIdAlias());
        assertEquals("urn:uuid:1234", s.discriminator());
    }

    @Test
    void parse_actionSuffix() {
        DcpScope s = DcpScope.parse("org.eclipse.dspace.dcp.vc.type:MembershipCredential:read");
        assertEquals("MembershipCredential", s.discriminator());
        assertEquals("read", s.action());
        assertTrue(s.hasAction());
    }

    @Test
    void parse_unqualified() {
        DcpScope s = DcpScope.parse("MembershipCredential");
        assertTrue(s.isUnqualified());
        assertFalse(s.isStandardAlias());
        assertEquals("MembershipCredential", s.discriminator());
    }

    @Test
    void parse_customAlias_isNotStandard() {
        DcpScope s = DcpScope.parse("custom.org:CustomType");
        assertFalse(s.isUnqualified());
        assertFalse(s.isStandardAlias());
        assertEquals("CustomType", s.discriminator());
    }

    @Test
    void parse_nullOrBlank() {
        assertTrue(DcpScope.parse(null).isUnqualified());
        assertEquals("", DcpScope.parse("  ").discriminator());
    }

    @Test
    void qualifyType() {
        assertEquals("org.eclipse.dspace.dcp.vc.type:MembershipCredential", DcpScope.qualifyType("MembershipCredential"));
        assertEquals("org.eclipse.dspace.dcp.vc.type:MembershipCredential",
                DcpScope.qualifyType("org.eclipse.dspace.dcp.vc.type:MembershipCredential"));
        assertEquals("org.eclipse.dspace.dcp.vc.id:abc", DcpScope.qualifyType("org.eclipse.dspace.dcp.vc.id:abc"));
        assertNull(DcpScope.qualifyType(null));
    }

    @Test
    void qualifyTypes_dropsBlankAndDuplicates() {
        assertEquals(List.of("org.eclipse.dspace.dcp.vc.type:A", "org.eclipse.dspace.dcp.vc.type:B"),
                DcpScope.qualifyTypes(Arrays.asList("A", " ", null, "org.eclipse.dspace.dcp.vc.type:A", "B")));
        assertTrue(DcpScope.qualifyTypes(null).isEmpty());
    }
}
