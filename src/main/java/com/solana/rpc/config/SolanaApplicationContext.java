package com.solana.rpc.config;

import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;

/**
 * Singleton-style application context for sharing configuration and RPC client instances.
 */
public final class SolanaApplicationContext {

    private static final SolanaConfig CONFIG = SolanaConfig.load();
    private static final RpcClient RPC_CLIENT = createRpcClient(CONFIG);

    private SolanaApplicationContext() {
    }

    public static SolanaConfig getConfig() {
        return CONFIG;
    }

    public static RpcClient getRpcClient() {
        return RPC_CLIENT;
    }

    private static RpcClient createRpcClient(SolanaConfig config) {
        String rpcUrl = config.getSolanaRpcUrl();
        if (rpcUrl == null || rpcUrl.isBlank()) {
            return new RpcClient(Cluster.TESTNET);
        }

        return new RpcClient(rpcUrl, config.getReadTimeoutMs(), config.getConnectTimeoutMs(), config.getWriteTimeoutMs());
    }
}
