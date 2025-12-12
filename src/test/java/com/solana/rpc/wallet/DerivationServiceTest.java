package com.solana.rpc.wallet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DerivationServiceTest {

    private static final String TEST_MNEMONIC = "urge pulp usage sister evidence arrest palm math please chief egg abuse";

    @Test
    void derivesExpectedPublicKeys() {
        DerivationService service = new DerivationService(TEST_MNEMONIC);

        String first = service.derivePublicKeyBase58(0, 0, 0);
        String second = service.derivePublicKeyBase58(0, 0, 1);

        assertEquals("2bahaF9qfc6pE5DJCKQ7AcZF1nXx5Jvf4NwkQib8uwbL", first);
        assertEquals("9LCBeEKbr17HV3Us8cWR7JrnNP6tLK6QDFtMv8RevjP1", second);
    }

    @Test
    void rejectsNegativePathValues() {
        DerivationService service = new DerivationService(TEST_MNEMONIC);

        assertThrows(IllegalArgumentException.class, () -> service.derive(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.derive(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.derive(0, 0, -1));
    }

    @Test
    void validatesMnemonicInput() {
        assertThrows(NullPointerException.class, () -> new DerivationService(null));
        assertThrows(IllegalArgumentException.class, () -> new DerivationService("   "));
        assertThrows(IllegalArgumentException.class, () -> new DerivationService("word"));
    }
}
