package com.solana.rpc.service;

import java.math.BigDecimal;

/**
 * Logical contract for interacting with the Solana testnet as described in the project design.
 * Implementations are responsible for key generation, persistence, and RPC communication.
 */
public interface SolanaWalletService {

    /**
     * Generates a new Solana testnet address and persists the associated keypair.
     *
     * @return base58-encoded public address string
     */
    String getNewAddress();

    /**
     * Retrieves the SOL balance for the provided base58-encoded address.
     *
     * @param base58Address Solana public key in base58 format
     * @return balance in SOL with up to nine fractional digits
     */
    BigDecimal getBalance(String base58Address);
}
