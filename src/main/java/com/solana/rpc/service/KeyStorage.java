package com.solana.rpc.service;

import org.p2p.solanaj.core.Account;

/**
 * Abstraction for persisting generated Solana keypairs.
 */
public interface KeyStorage {

    /**
     * Persist the provided account's keypair.
     *
     * @param account generated account containing public and private keys
     */
    void save(Account account);
}
