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
    String getNewAddress();

    /**
     * Generates a new Solana testnet address using the provided label and persists the associated
     * derivation metadata.
     *
     * @param label human-readable label for the new address
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

    /**
     * Transfers SOL from a derived account to a target address.
     *
     * @param fromAddress base58-encoded sender public key derived from the configured mnemonic
     * @param toAddress base58-encoded recipient public key
     * @param amount amount in SOL to transfer
     * @return transaction signature in base58 format
     */
    String transferSol(String fromAddress, String toAddress, BigDecimal amount);

    /**
     * Retrieves the SPL token balance for the provided owner address and token mint.
     *
     * @param base58Address base58-encoded owner public key
     * @param tokenMintAddress base58-encoded SPL token mint address
     * @return token balance in token units with mint decimals applied
     */
    BigDecimal getTokenBalance(String base58Address, String tokenMintAddress);

    /**
     * Transfers SPL tokens from a derived account to a target address.
     *
     * @param fromAddress base58-encoded sender public key derived from the configured mnemonic
     * @param toAddress base58-encoded recipient public key
     * @param amount token amount to transfer
     * @param tokenAddress base58-encoded SPL token mint address
     * @return transaction signature in base58 format
     */
    String transferSolToken(String fromAddress, String toAddress, BigDecimal amount, String tokenAddress);
}
