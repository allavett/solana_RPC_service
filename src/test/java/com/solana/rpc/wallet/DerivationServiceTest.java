package com.solana.rpc.wallet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DerivationServiceTest {

    private static final String TEST_MNEMONIC = "urge pulp usage sister evidence arrest palm math please chief egg abuse";

    @Test
    void derivesExpectedPublicKeys() {
        DerivationService service = new DerivationService(TEST_MNEMONIC);

        String first = service.derivePublicKeyBase58(0, 0);
        String second = service.derivePublicKeyBase58(1, 0);

        assertEquals("FfCEC4bh9hCuo2nANx7n8MVSumz7YqxT21sJ92YcTprg", first);
        assertEquals("7SntRv1bwxMwV5za2b1HTN5fjMJdX6qSLugg8L8FdDjY", second);
    }

    @Test
    void rejectsNegativePathValues() {
        DerivationService service = new DerivationService(TEST_MNEMONIC);

        assertThrows(IllegalArgumentException.class, () -> service.derive(-1));
    }

    @Test
    void derivationIsDeterministicForMnemonic() {
        DerivationService firstInstance = new DerivationService(TEST_MNEMONIC);
        DerivationService secondInstance = new DerivationService(TEST_MNEMONIC);

        String firstDerived = firstInstance.derivePublicKeyBase58(5, 0);
        String secondDerived = secondInstance.derivePublicKeyBase58(5, 0);

        assertEquals(firstDerived, secondDerived);
    }

    @Test
    void validatesMnemonicInput() {
        assertThrows(NullPointerException.class, () -> new DerivationService(null));
        assertThrows(IllegalArgumentException.class, () -> new DerivationService("   "));
        assertThrows(IllegalArgumentException.class, () -> new DerivationService("word"));
    }
}
