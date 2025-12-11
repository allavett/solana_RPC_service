package com.solana.rpc.service;

import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Default implementation of {@link SolanaWalletService} backed by the Solanaj RPC client.
 */
public class SolanajWalletService implements SolanaWalletService {

    private static final BigDecimal LAMPORTS_PER_SOL = new BigDecimal("1000000000");

    private final RpcClient rpcClient;
    private final KeyStorage keyStorage;

    public SolanajWalletService() {
        this(createDefaultClient(), new InMemoryKeyStorage());
    }

    public SolanajWalletService(RpcClient rpcClient) {
        this(rpcClient, new InMemoryKeyStorage());
    }

    public SolanajWalletService(RpcClient rpcClient, KeyStorage keyStorage) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient must not be null");
        this.keyStorage = Objects.requireNonNull(keyStorage, "keyStorage must not be null");
    }

    @Override
    public String getNewAddress() {
        Account account = new Account();
        try {
            keyStorage.save(account);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to persist generated keypair", e);
        }
        return account.getPublicKey().toBase58();
    }

    @Override
    public BigDecimal getBalance(String base58Address) {
        if (base58Address == null || base58Address.isBlank()) {
            throw new IllegalArgumentException("Address must not be null or blank");
        }

        final PublicKey publicKey;
        try {
            publicKey = new PublicKey(base58Address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Address is not a valid base58-encoded public key", e);
        }

        try {
            RpcApi api = rpcClient.getApi();
            long lamports = api.getBalance(publicKey);
            return BigDecimal.valueOf(lamports).divide(LAMPORTS_PER_SOL, 9, RoundingMode.DOWN);
        } catch (RpcException e) {
            throw new IllegalStateException("Failed to fetch balance from Solana RPC", e);
        }
    }

    private static RpcClient createDefaultClient() {
        ServiceConfiguration configuration = ConfigLoader.loadConfiguration();

        String rpcUrl = configuration.getSolanaRpcUrl();
        if (rpcUrl != null && !rpcUrl.isBlank()) {
            return new RpcClient(rpcUrl);
        }

        return new RpcClient(Cluster.TESTNET);
    }
}
