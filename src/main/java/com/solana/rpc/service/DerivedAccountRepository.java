package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for derived accounts, supporting simple CRUD operations.
 */
public interface DerivedAccountRepository {

    DerivedAccount save(DerivedAccount derivedAccount);

    List<DerivedAccount> findAll();

    Optional<DerivedAccount> findByLabel(String label);

    Optional<DerivedAccount> findByPublicKey(String publicKey);

    boolean deleteByLabel(String label);

    boolean deleteByPublicKey(String publicKey);
}
