package com.solana.rpc.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Container for service configuration values loaded from config.json.
 */
public class ServiceConfiguration {

    @JsonProperty("SolanaRpcUrl")
    private String solanaRpcUrl;

    public String getSolanaRpcUrl() {
        return solanaRpcUrl;
    }
}
