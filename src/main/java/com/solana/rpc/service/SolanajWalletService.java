package com.solana.rpc.service;

import com.solana.rpc.config.SolanaApplicationContext;
import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.wallet.DerivationService;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of {@link SolanaWalletService} backed by the Solanaj RPC client and deterministic key derivation.
 */
public class SolanajWalletService implements SolanaWalletService {

    private static final Logger LOGGER = Logger.getLogger(SolanajWalletService.class.getName());
    private static final BigDecimal LAMPORTS_PER_SOL = new BigDecimal("1000000000");
    private static final int DEFAULT_ACCOUNT = 0;
    private static final int DEFAULT_CHANGE = 0;

    private final RpcClient rpcClient;
    private final DerivationService derivationService;
    private final DerivedAccountRepository accountRepository;
    private final KeyStorage keyStorage;

    public SolanajWalletService() {
        this(SolanaApplicationContext.getRpcClient(),
                new DerivationService(SolanaApplicationContext.getConfig().getMnemonic()),
                new InMemoryDerivedAccountRepository(),
                new InMemoryKeyStorage());

        LOGGER.info(() -> "Initialized SolanajWalletService with RPC URL="
                + SolanaApplicationContext.getConfig().getSolanaRpcUrl()
                + " (connectTimeoutMs=" + SolanaApplicationContext.getConfig().getConnectTimeoutMs()
                + ", readTimeoutMs=" + SolanaApplicationContext.getConfig().getReadTimeoutMs()
                + ", writeTimeoutMs=" + SolanaApplicationContext.getConfig().getWriteTimeoutMs() + ")");
    }

    public SolanajWalletService(RpcClient rpcClient, DerivationService derivationService,
                                DerivedAccountRepository accountRepository, KeyStorage keyStorage) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient must not be null");
        this.derivationService = Objects.requireNonNull(derivationService, "derivationService must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.keyStorage = Objects.requireNonNull(keyStorage, "keyStorage must not be null");
    }

    @Override
    public List<DerivedAccount> listAccounts() {
        return Collections.unmodifiableList(accountRepository.findAll());
    }

    @Override
    public String getNewAddress() {
        int nextIndex = determineNextIndex();
        String autoLabel = "account-" + nextIndex;
        return createAndPersistAddress(autoLabel, nextIndex);
    }

    @Override
    public String getNewAddress(String label) {
        validateLabel(label);
        if (accountRepository.findByLabel(label).isPresent()) {
            throw new IllegalArgumentException("Label already exists: " + label);
        }

        int nextIndex = determineNextIndex();
        return createAndPersistAddress(label, nextIndex);
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
            LOGGER.info(() -> "Requesting balance from RPC for address " + base58Address
                    + " via endpoint " + SolanaApplicationContext.getConfig().getSolanaRpcUrl());
            long lamports = api.getBalance(publicKey);
            LOGGER.info(() -> "Received balance (lamports): " + lamports);
            return BigDecimal.valueOf(lamports).divide(LAMPORTS_PER_SOL, 9, RoundingMode.DOWN);
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC balance call failed", e);
            throw new IllegalStateException("Failed to fetch balance from Solana RPC", e);
        }
    }

    @Override
    public BigDecimal getBalanceByLabel(String label) {
        validateLabel(label);
        DerivedAccount account = accountRepository.findByLabel(label)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account label: " + label));
        return getBalance(account.getPublicKey());
    }

    private void validateLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label must not be null or blank");
        }
    }

    private int determineNextIndex() {
        return accountRepository.findAll().stream()
                .filter(account -> account.getAccount() == DEFAULT_ACCOUNT && account.getChange() == DEFAULT_CHANGE)
                .mapToInt(DerivedAccount::getIndex)
                .max()
                .orElse(-1) + 1;
    }

    private String createAndPersistAddress(String label, int index) {
        Account derivedAccount = derivationService.derive(DEFAULT_ACCOUNT, DEFAULT_CHANGE, index);
        keyStorage.save(derivedAccount);
        String publicKey = derivedAccount.getPublicKey().toBase58();

        DerivedAccount metadata = new DerivedAccount(label, DEFAULT_ACCOUNT, DEFAULT_CHANGE, index, publicKey);
        accountRepository.save(metadata);

        return publicKey;
    }
}
