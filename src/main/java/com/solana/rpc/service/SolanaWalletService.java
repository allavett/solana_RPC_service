package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;

import java.math.BigDecimal;
import java.util.List;

/**
 * Logical contract for interacting with the Solana testnet as described in the project design.
 * Implementations are responsible for key generation, persistence, and RPC communication.
 */
public interface SolanaWalletService {

    /**
     * Lists all known derived accounts stored in the repository.
     *
     * @return immutable list of derived account metadata
     */
    List<DerivedAccount> listAccounts();

    /**
     * Generates a new Solana testnet address and persists the associated derivation metadata.
     *
     * @return base58-encoded public address string
     */
    String getNewAddress(String label);

    /**
     * Retrieves the SOL balance for the provided base58-encoded address.
     *
     * @param base58Address Solana public key in base58 format
     * @return balance in SOL with up to nine fractional digits
     */
    BigDecimal getBalance(String base58Address);

    /**
     * Retrieves the SOL balance for the derived account identified by the supplied label.
     *
     * @param label human-readable label associated with a derived account
     * @return balance in SOL with up to nine fractional digits
     */
    BigDecimal getBalanceByLabel(String label);
}
