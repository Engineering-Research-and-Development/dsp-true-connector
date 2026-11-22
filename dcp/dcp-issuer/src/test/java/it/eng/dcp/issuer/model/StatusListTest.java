package it.eng.dcp.issuer.model;

import it.eng.dcp.common.model.ProfileId;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StatusListTest {

    @Test
    @DisplayName("A new list has every bit 0 and allocates indices in order without setting their bits")
    void allocateIndex_keepsStatusBitZero() {
        StatusList list = newList(16);

        assertEquals(0, list.allocateIndex());
        assertEquals(1, list.allocateIndex());

        assertTrue(list.isAllocated(0));
        assertTrue(list.isAllocated(1));
        assertFalse(list.isAllocated(2));
        assertFalse(list.isSet(0));
        assertFalse(list.isSet(1));
        assertArrayEquals(new byte[2], list.getBitstring());
    }

    @Test
    @DisplayName("A full list refuses to allocate")
    void allocateIndex_fullList_throws() {
        StatusList list = newList(8);
        for (int i = 0; i < 8; i++) {
            list.allocateIndex();
        }

        assertTrue(list.isFull());
        assertThrows(IllegalStateException.class, list::allocateIndex);
    }

    @Test
    @DisplayName("setBit sets the spec-ordered bit: index 0 is the left-most bit of the first byte")
    void setBit_usesLeftMostBitOrder() {
        StatusList list = newList(16);
        for (int i = 0; i < 10; i++) {
            list.allocateIndex();
        }

        list.setBit(0);
        list.setBit(9);

        assertTrue(list.isSet(0));
        assertTrue(list.isSet(9));
        assertFalse(list.isSet(1));
        byte[] bits = list.getBitstring();
        assertEquals((byte) 0x80, bits[0]);
        assertEquals((byte) 0x40, bits[1]);
    }

    @Test
    @DisplayName("setBit refuses an index that has not been allocated")
    void setBit_unallocated_throws() {
        StatusList list = newList(16);
        list.allocateIndex();

        assertThrows(IllegalArgumentException.class, () -> list.setBit(1));
        assertThrows(IllegalArgumentException.class, () -> list.setBit(-1));
    }

    @Test
    @DisplayName("isSet refuses an index outside the list")
    void isSet_outOfRange_throws() {
        StatusList list = newList(8);

        assertThrows(IllegalArgumentException.class, () -> list.isSet(8));
        assertThrows(IllegalArgumentException.class, () -> list.isSet(-1));
    }

    @Test
    @DisplayName("getBitstring returns a copy")
    void getBitstring_returnsCopy() {
        StatusList list = newList(8);
        list.getBitstring()[0] = (byte) 0xFF;

        assertArrayEquals(new byte[1], list.getBitstring());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -8, 7, 12})
    @DisplayName("The builder refuses a size that is not a positive multiple of 8")
    void build_invalidSize_throws(int size) {
        StatusList.Builder builder = validBuilder().size(size);

        assertThrows(ValidationException.class, builder::build);
    }

    @Test
    @DisplayName("The builder refuses a bitstring whose length does not match the size")
    void build_bitstringLengthMismatch_throws() {
        StatusList.Builder builder = validBuilder().size(16).bitstring(new byte[1]);

        assertThrows(ValidationException.class, builder::build);
    }

    @Test
    @DisplayName("The builder refuses a next index outside the list")
    void build_nextIndexOutOfRange_throws() {
        assertThrows(ValidationException.class, () -> validBuilder().size(8).nextIndex(9).build());
        assertThrows(ValidationException.class, () -> validBuilder().size(8).nextIndex(-1).build());
    }

    @Test
    @DisplayName("The builder refuses a missing required field")
    void build_missingRequiredField_throws() {
        assertThrows(ValidationException.class, () -> validBuilder().id(null).build());
        assertThrows(ValidationException.class, () -> validBuilder().url(null).build());
        assertThrows(ValidationException.class, () -> validBuilder().issuerDid(null).build());
        assertThrows(ValidationException.class, () -> validBuilder().statusPurpose(null).build());
        assertThrows(ValidationException.class, () -> validBuilder().profile(null).build());
    }

    private static StatusList newList(int size) {
        return validBuilder().size(size).build();
    }

    private static StatusList.Builder validBuilder() {
        return StatusList.Builder.newInstance()
                .id("list-1")
                .url("http://issuer.example.com/issuer/status/list-1")
                .issuerDid("did:web:issuer.example.com")
                .statusPurpose("revocation")
                .profile(ProfileId.VC20_BSSL_JWT)
                .size(8);
    }
}
