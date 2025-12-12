package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDerivedAccountRepositoryTest {

    private InMemoryDerivedAccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDerivedAccountRepository();
    }

    @Test
    void savesAndFindsByLabelAndPublicKey() {
        DerivedAccount account = new DerivedAccount("first", 0, 0, 0, "pubKey1");

        repository.save(account);

        assertEquals(account, repository.findByLabel("first").orElseThrow());
        assertEquals(account, repository.findByPublicKey("pubKey1").orElseThrow());
    }

    @Test
    void deletesByEitherKey() {
        DerivedAccount account = new DerivedAccount("first", 0, 0, 0, "pubKey1");
        repository.save(account);

        assertTrue(repository.deleteByPublicKey("pubKey1"));
        assertTrue(repository.findAll().isEmpty());

        repository.save(account);
        assertTrue(repository.deleteByLabel("first"));
        assertTrue(repository.findByLabel("first").isEmpty());
        assertTrue(repository.findByPublicKey("pubKey1").isEmpty());
    }

    @Test
    void findByPublicKeyDelegatesToLabelMapping() {
        DerivedAccount account = new DerivedAccount("primary", 0, 0, 1, "pubKey2");
        repository.save(account);

        Optional<DerivedAccount> lookup = repository.findByPublicKey("pubKey2");

        assertTrue(lookup.isPresent());
        assertEquals("primary", lookup.get().getLabel());
        assertEquals(1, lookup.get().getIndex());
    }
}
