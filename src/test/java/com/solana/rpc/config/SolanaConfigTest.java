package com.solana.rpc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolanaConfigTest {

    @Test
    void validateFailsWhenMnemonicMissing() {
        SolanaConfig config = new SolanaConfig("   ", "https://api.testnet.solana.com", 20000, 10000, 20000);

        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void validateSucceedsWithMnemonic() {
        SolanaConfig config = new SolanaConfig("seed phrase present", "https://api.testnet.solana.com", 20000, 10000, 20000);

        assertDoesNotThrow(config::validate);
    }
}
