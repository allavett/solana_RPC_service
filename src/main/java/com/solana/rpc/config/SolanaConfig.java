package com.solana.rpc.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Configuration holder for Solana RPC interactions.
 */
public class SolanaConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONFIG_RESOURCE = "/config.json";

    @JsonProperty("Mnemonic")
    private String mnemonic;

    @JsonProperty("SolanaRpcUrl")
    private String solanaRpcUrl;

    @JsonProperty("ReadTimeoutMs")
    private int readTimeoutMs = 20_000;

    @JsonProperty("ConnectTimeoutMs")
    private int connectTimeoutMs = 10_000;

    @JsonProperty("WriteTimeoutMs")
    private int writeTimeoutMs = 20_000;

    SolanaConfig() {
        // Jackson constructor
    }

    SolanaConfig(String mnemonic, String solanaRpcUrl, int readTimeoutMs, int connectTimeoutMs, int writeTimeoutMs) {
        this.mnemonic = mnemonic;
        this.solanaRpcUrl = solanaRpcUrl;
        this.readTimeoutMs = readTimeoutMs;
        this.connectTimeoutMs = connectTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
    }

    public static SolanaConfig load() {
        InputStream inputStream = SolanaConfig.class.getResourceAsStream(CONFIG_RESOURCE);
        if (inputStream == null) {
            throw new IllegalStateException("config.json not found on the classpath; mnemonic is required for startup");
        }

        try {
            SolanaConfig configuration = OBJECT_MAPPER.readValue(inputStream, SolanaConfig.class);
            if (configuration == null) {
                throw new IllegalStateException("config.json is empty; mnemonic is required for startup");
            }
            configuration.validate();
            return configuration;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load configuration from config.json", e);
        }
    }

    public void validate() {
        if (mnemonic == null || mnemonic.isBlank()) {
            throw new IllegalStateException("Mnemonic is required in config.json (field \"Mnemonic\")");
        }
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public String getSolanaRpcUrl() {
        return solanaRpcUrl;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    @Override
    public String toString() {
        return "SolanaConfig{" +
                "mnemonic='" + (mnemonic == null ? "" : "***") + '\'' +
                ", solanaRpcUrl='" + solanaRpcUrl + '\'' +
                ", readTimeoutMs=" + readTimeoutMs +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", writeTimeoutMs=" + writeTimeoutMs +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SolanaConfig that)) return false;
        return readTimeoutMs == that.readTimeoutMs && connectTimeoutMs == that.connectTimeoutMs && writeTimeoutMs == that.writeTimeoutMs && Objects.equals(mnemonic, that.mnemonic) && Objects.equals(solanaRpcUrl, that.solanaRpcUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mnemonic, solanaRpcUrl, readTimeoutMs, connectTimeoutMs, writeTimeoutMs);
    }
}
