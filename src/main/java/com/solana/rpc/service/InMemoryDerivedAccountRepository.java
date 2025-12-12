package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory storage for derived accounts.
 */
public class InMemoryDerivedAccountRepository implements DerivedAccountRepository {

    private final Map<String, DerivedAccount> accountsByLabel = new ConcurrentHashMap<>();
    private final Map<String, String> labelByPublicKey = new ConcurrentHashMap<>();

    @Override
    public DerivedAccount save(DerivedAccount derivedAccount) {
        Objects.requireNonNull(derivedAccount, "derivedAccount must not be null");

        accountsByLabel.put(derivedAccount.getLabel(), derivedAccount);
        labelByPublicKey.put(derivedAccount.getPublicKey(), derivedAccount.getLabel());
        return derivedAccount;
    }

    @Override
    public List<DerivedAccount> findAll() {
        return new ArrayList<>(accountsByLabel.values());
    }

    @Override
    public Optional<DerivedAccount> findByLabel(String label) {
        return Optional.ofNullable(accountsByLabel.get(label));
    }

    @Override
    public Optional<DerivedAccount> findByPublicKey(String publicKey) {
        String label = labelByPublicKey.get(publicKey);
        if (label == null) {
            return Optional.empty();
        }

        return findByLabel(label);
    }

    @Override
    public boolean deleteByLabel(String label) {
        DerivedAccount removed = accountsByLabel.remove(label);
        if (removed == null) {
            return false;
        }

        labelByPublicKey.remove(removed.getPublicKey());
        return true;
    }

    @Override
    public boolean deleteByPublicKey(String publicKey) {
        String label = labelByPublicKey.remove(publicKey);
        if (label == null) {
            return false;
        }

        return accountsByLabel.remove(label) != null;
    }
}
