package com.solana.rpc.service;

import org.p2p.solanaj.core.Account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory key storage for generated accounts.
 */
public class InMemoryKeyStorage implements KeyStorage {

    private final List<Account> accounts = new CopyOnWriteArrayList<>();

    @Override
    public void save(Account account) {
        accounts.add(account);
    }

    public List<Account> getAccounts() {
        return List.copyOf(accounts);
    }
}
