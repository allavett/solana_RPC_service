package com.solana.rpc.model;

import java.util.Objects;

/**
 * Value object representing a derived Solana account path and its public key.
 */
public class DerivedAccount {

    private final String label;
    private final int account;
    private final String publicKey;

    public DerivedAccount(String label, int account, String publicKey) {
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.account = account;
    }

    public String getLabel() {
        return label;
    }

    public int getAccount() {
        return account;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "DerivedAccount{" +
                "label='" + label + '\'' +
                ", account=" + account +
                ", publicKey='" + publicKey + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DerivedAccount that = (DerivedAccount) o;
        return account == that.account && label.equals(that.label) && publicKey.equals(that.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, account, publicKey);
    }
}
