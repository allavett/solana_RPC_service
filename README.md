# Solana Testnet Java Service – Design Spec

## 1. Purpose

A small Java 17 service that talks to the **Solana testnet RPC endpoint** and provides:

1. `getNewAddress` – generates a new SOL address.
2. `getBalance` – returns SOL balance for a given address.

No transactions or mainnet support; read-only plus key generation.

---

## 2. Tech Stack

* **Language:** Java 17
* **Build:** Gradle (project uses this as package manager)
* **Library:** `com.mmorrell:solanaj:1.27.3`
* **Network:** Solana **testnet** (default RPC URL `https://api.testnet.solana.com` or equivalent testnet cluster)

---

## 3. Responsibilities

**Service component (SolanaWalletService):**

* Encapsulates all Solana-specific logic.
* Uses Solanaj RPC client configured for testnet.
* Uses a key storage abstraction to persist generated keypairs.

**RPC client setup:**

* Centralized factory/provider that returns a configured Solanaj RPC client pointing to testnet.
* Uses configuration to set RPC URL and timeouts.

**Key storage:**

* Abstract interface for persisting and retrieving keypairs (public/private keys).
* Backed by in-memory store, file store, database, or KMS (implementation-specific).

---

## 4. Public Interface (Logical)

### 4.1 `getNewAddress()`

* **Input:** none
* **Behavior:**

  * Generates a new Ed25519 keypair via Solanaj.
  * Stores the private key and associated public key via key storage.
* **Output:**

  * Returns a **base58-encoded** Solana address (public key string) on testnet.
* **Failure cases:**

  * Key generation failure.
  * Storage failure for the new keypair.

### 4.2 `getBalance(base58Address)`

* **Input:**

  * `base58Address` – string, base58-encoded Solana public key.
* **Behavior:**

  * Validates that the address is non-empty and structurally valid as a Solana public key.
  * Invokes Solanaj RPC client’s balance API on testnet.
  * Receives balance in lamports and converts to SOL (1 SOL = 1,000,000,000 lamports).
* **Output:**

  * Balance as a decimal number in **SOL** with up to 9 fractional digits.
* **Failure cases:**

  * Invalid address input (report as validation error).
  * RPC/network errors (report as RPC error).
  * Unexpected internal errors (wrapped and logged).

---

## 5. Solana Integration

* Service talks only to **testnet**.
* RPC endpoint configuration:

  * Default: pre-defined testnet cluster.
  * Optional override via environment variable or application properties (e.g., `SOLANA_RPC_URL`).
* RPC client concerns:

  * Connection and read timeouts.
  * Optional simple retry on transient failures (configurable).

---

## 6. Key Management & Security

* Generated keypairs must be stored via a **key storage abstraction** (not hard-coded storage).
* Private keys:

  * Never logged.
  * Not exposed by the public interface.
  * Encrypted at rest in production-ready setups.
* Key storage implementation can vary by environment (in-memory for dev, secure backend for prod).

---

## 7. Configuration & Error Handling

**Configuration:**

* RPC URL, timeouts, and retries supplied via config (env vars, properties, etc.).
* Ability to switch between default testnet endpoint and a custom testnet RPC URL.

**Error handling:**

* Clear separation of:

  * Input validation errors (e.g., malformed address).
  * RPC/network errors (testnet unreachable, timeout, RPC failure).
  * Internal/unexpected errors.
* Service exposes meaningful error types/messages to callers (e.g., for mapping to HTTP status codes in a REST layer).

---

## 8. Testing

**Unit tests:**

* RPC client and key storage mocked.
* `getNewAddress`:

  * Verifies new address format and that key storage is called.
* `getBalance`:

  * Valid address and expected balance.
  * Invalid address → validation error.
  * Simulated RPC failures → RPC error.

**Integration tests (optional):**

* Run against real Solana testnet:

  * Generate an address and confirm that balance calls succeed.
  * Query the balance of known funded testnet addresses, if available.

