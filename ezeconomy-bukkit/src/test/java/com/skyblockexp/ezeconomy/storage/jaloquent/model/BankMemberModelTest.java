package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Factory is in the same package: BankMemberModelFactory

/**
 * Unit tests for {@link BankMemberModel}.
 *
 * <p>Pure in-memory tests: no database, no Bukkit server.
 */
class BankMemberModelTest {

    private static final UUID FIXED_UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    // --- idFor ---

    @Test
    void idFor_encodesNameAndUuid() {
        assertEquals(
            "mybank_bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            BankMemberModel.idFor("mybank", FIXED_UUID)
        );
    }

    @Test
    void idFor_differentiatesByBank() {
        UUID uuid = UUID.randomUUID();
        assertNotEquals(
            BankMemberModel.idFor("bankA", uuid),
            BankMemberModel.idFor("bankB", uuid)
        );
    }

    @Test
    void idFor_differentiatesByUuid() {
        assertNotEquals(
            BankMemberModel.idFor("bank", UUID.randomUUID()),
            BankMemberModel.idFor("bank", UUID.randomUUID())
        );
    }

    // --- create / isOwner ---

    @Test
    void create_owner_true_isOwnerReturnsTrue() {
        UUID uuid = UUID.randomUUID();
        BankMemberModel m = BankMemberModel.create("bank", uuid, true);
        assertTrue(m.isOwner());
    }

    @Test
    void create_owner_false_isOwnerReturnsFalse() {
        UUID uuid = UUID.randomUUID();
        BankMemberModel m = BankMemberModel.create("bank", uuid, false);
        assertFalse(m.isOwner());
    }

    // --- isOwner Number coercion (for JDBC integer columns) ---

    @Test
    void isOwner_withIntegerOne_returnsTrue() {
        BankMemberModel m = new BankMemberModel("id");
        m.set("owner", 1);            // DB stores BOOLEAN as INTEGER
        assertTrue(m.isOwner());
    }

    @Test
    void isOwner_withIntegerZero_returnsFalse() {
        BankMemberModel m = new BankMemberModel("id");
        m.set("owner", 0);
        assertFalse(m.isOwner());
    }

    @Test
    void isOwner_withStringTrue_returnsTrue() {
        BankMemberModel m = new BankMemberModel("id");
        m.set("owner", "true");
        assertTrue(m.isOwner());
    }

    @Test
    void isOwner_whenNull_returnsFalse() {
        BankMemberModel m = new BankMemberModel("id");
        assertFalse(m.isOwner());
    }

    // --- getMemberUuid ---

    @Test
    void getMemberUuid_returnsUuidString() {
        String uuid = UUID.randomUUID().toString();
        BankMemberModel m = new BankMemberModelFactory()
                .state(Map.of("uuid", uuid))
                .make();
        assertEquals(uuid, m.getMemberUuid());
    }

    // --- getBank ---

    @Test
    void getBank_returnsBankName() {
        BankMemberModel m = new BankMemberModelFactory()
                .state(Map.of("bank", "vault"))
                .make();
        assertEquals("vault", m.getBank());
    }

    // --- composite key set by create ---

    @Test
    void create_setsId_toIdFor() {
        UUID uuid = UUID.randomUUID();
        BankMemberModel m = BankMemberModel.create("bank", uuid, true);
        assertEquals(BankMemberModel.idFor("bank", uuid), m.getId());
    }

    // --- PREFIX constant ---

    @Test
    void prefix_matchesExpectedRepositoryKey() {
        assertEquals("bank_members", BankMemberModel.PREFIX);
    }
}
