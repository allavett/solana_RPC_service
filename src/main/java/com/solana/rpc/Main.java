package com.solana.rpc;

import com.solana.rpc.config.SolanaApplicationContext;
import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.service.SolanaWalletService;
import com.solana.rpc.service.SolanajWalletService;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple console entrypoint that exercises the SolanaWalletService against the
 * Solana testnet. Generates a new address using the configured mnemonic,
 * prints its balance, and lists known derived accounts.
    */
    public class Main {

    public static void main(String[] args) {
        configureProxyFromEnv();

        SolanaWalletService walletService = new SolanajWalletService();

        printNetworkDiagnostics();

        System.out.println("=== Solana Testnet Wallet Demo ===");
        System.out.println("RPC endpoint: " + SolanaApplicationContext.getConfig().getSolanaRpcUrl());

        String label = "demo-" + Instant.now().toEpochMilli();
        System.out.println("\nCreating a new derived address with label: " + label);

        String address = walletService.getNewAddress(label);
        System.out.println("New address: " + address);

        BigDecimal balance = walletService.getBalance(address);
        System.out.println("Balance for " + address + ": " + balance.stripTrailingZeros().toPlainString() + " SOL");

        List<DerivedAccount> accounts = walletService.listAccounts();
        System.out.println("\nDerived accounts held in memory:");
        for (DerivedAccount account : accounts) {
            System.out.printf(" - %s => %s (account %d, change %d, index %d)%n",
                    account.getLabel(), account.getPublicKey(),
                    account.getAccount(), account.getChange(), account.getIndex());
        }

        System.out.println("\nFinished demo run against Solana testnet.");
    }

    private static void configureProxyFromEnv() {
        configureProxy("HTTP_PROXY", "http");
        configureProxy("HTTPS_PROXY", "https");
        configureNoProxy("NO_PROXY");
    }

    private static void configureProxy(String envKey, String protocol) {
        String value = firstPresentEnv(envKey);
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            URI uri = parseProxyUri(value);
            if (uri.getHost() == null || uri.getPort() == -1) {
                System.err.println("Ignoring proxy env var " + envKey + " because host or port is missing: " + value);
                return;
            }

            System.setProperty(protocol + ".proxyHost", uri.getHost());
            System.setProperty(protocol + ".proxyPort", String.valueOf(uri.getPort()));
            System.out.println("Configured " + protocol + " proxy from " + envKey + " => " + uri.getHost() + ":" + uri.getPort());
        } catch (URISyntaxException e) {
            System.err.println("Could not parse " + envKey + " for proxy configuration: " + e.getMessage());
        }
    }

    private static void configureNoProxy(String envKey) {
        String value = firstPresentEnv(envKey);
        if (value == null || value.isBlank()) {
            return;
        }

        String nonProxyHosts = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.startsWith(".") ? host.substring(1) : host)
                .collect(Collectors.joining("|"));

        if (nonProxyHosts.isEmpty()) {
            return;
        }

        System.setProperty("http.nonProxyHosts", nonProxyHosts);
        System.setProperty("https.nonProxyHosts", nonProxyHosts);
        System.out.println("Configured non-proxy hosts from " + envKey + " => " + nonProxyHosts);
    }

    private static URI parseProxyUri(String raw) throws URISyntaxException {
        String trimmed = raw.trim();
        if (!trimmed.contains("://")) {
            trimmed = "http://" + trimmed;
        }
        return new URI(trimmed);
    }

    private static String firstPresentEnv(String key) {
        String primary = System.getenv(key);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        String lower = System.getenv(key.toLowerCase());
        if (lower != null && !lower.isBlank()) {
            return lower;
        }
        return null;
    }

    private static void printNetworkDiagnostics() {
        System.out.println("=== RPC Connectivity Diagnostics ===");

        SolanaApplicationContext.getConfig();
        String rpcUrl = SolanaApplicationContext.getConfig().getSolanaRpcUrl();
        System.out.println("Configured RPC URL: " + rpcUrl);
        System.out.println("Configured timeouts (ms): connect="
                + SolanaApplicationContext.getConfig().getConnectTimeoutMs()
                + ", read=" + SolanaApplicationContext.getConfig().getReadTimeoutMs()
                + ", write=" + SolanaApplicationContext.getConfig().getWriteTimeoutMs());

        System.out.println("System proxy environment variables:");
        for (String key : List.of("HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY")) {
            System.out.printf(" - %s=%s%n", key, System.getenv(key));
        }

        System.out.println("System properties influencing networking:");
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String propKey = String.valueOf(entry.getKey());
            if (propKey.startsWith("http.") || propKey.startsWith("https.")) {
                System.out.printf(" - %s=%s%n", propKey, entry.getValue());
            }
        }

        try {
            URI uri = new URI(rpcUrl);
            System.out.println("Resolved hosts for: " + uri.getHost());
            Arrays.stream(InetAddress.getAllByName(uri.getHost()))
                    .forEach(addr -> System.out.println(" - " + addr.getHostAddress() + " (" + addr.getClass().getSimpleName() + ")"));
            performJavaHealthProbe(uri);
        } catch (UnknownHostException e) {
            System.err.println("Unable to resolve host for RPC URL: " + e.getMessage());
        } catch (URISyntaxException e) {
            System.err.println("Invalid RPC URL configured: " + rpcUrl + " => " + e.getMessage());
        }

        System.out.println("=== End Diagnostics ===\n");
    }

    private static void performJavaHealthProbe(URI uri) {
        String payload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getHealth\"}";
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(SolanaApplicationContext.getConfig().getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(SolanaApplicationContext.getConfig().getConnectTimeoutMs()))
                .build();

        System.out.println("Attempting Java HTTP health probe (getHealth)...");
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Health probe status: " + response.statusCode());
            String body = response.body();
            if (body != null && body.length() > 400) {
                body = body.substring(0, 400) + "...";
            }
            System.out.println("Health probe body: " + body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Health probe interrupted: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Health probe failed: " + e.getMessage());
        }
    }
}
