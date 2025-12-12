package com.solana.rpc.model;

import java.util.Objects;

/**
 * Value object representing a derived Solana account path and its public key.
 */
public class DerivedAccount {

    private final String label;
    private final int account;
    private final int change;
    private final int index;
    private final String publicKey;

    public DerivedAccount(String label, int account, int change, int index, String publicKey) {
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.account = account;
        this.change = change;
        this.index = index;
    }

    public String getLabel() {
        return label;
    }

    public int getAccount() {
        return account;
    }

    public int getChange() {
        return change;
    }

    public int getIndex() {
        return index;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "DerivedAccount{" +
                "label='" + label + '\'' +
                ", account=" + account +
                ", change=" + change +
                ", index=" + index +
                ", publicKey='" + publicKey + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DerivedAccount that = (DerivedAccount) o;
        return account == that.account && change == that.change && index == that.index && label.equals(that.label) && publicKey.equals(that.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, account, change, index, publicKey);
    }
}
