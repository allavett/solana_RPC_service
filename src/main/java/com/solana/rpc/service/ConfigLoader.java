package com.solana.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Loads service configuration from the classpath config.json file.
 */
public final class ConfigLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONFIG_RESOURCE = "/config.json";

    private ConfigLoader() {
    }

    public static ServiceConfiguration loadConfiguration() {
        InputStream inputStream = ConfigLoader.class.getResourceAsStream(CONFIG_RESOURCE);
        if (inputStream == null) {
            return new ServiceConfiguration();
        }

        try {
            ServiceConfiguration configuration = OBJECT_MAPPER.readValue(inputStream, ServiceConfiguration.class);
            return configuration != null ? configuration : new ServiceConfiguration();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load service configuration from config.json", e);
        }
    }
}
