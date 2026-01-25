package com.solana.rpc.service;

import com.solana.rpc.config.SolanaApplicationContext;
import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.wallet.DerivationService;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.rpc.types.TokenResultObjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Override
    public String transferSol(String fromAddress, String toAddress, BigDecimal amount) {
        PublicKey fromPublicKey = parseRequiredPublicKey(fromAddress, "Sender");
        PublicKey toPublicKey = parseRequiredPublicKey(toAddress, "Recipient");
        long lamports = validateAndConvertAmount(amount);

        DerivedAccount derivedAccount = accountRepository.findByPublicKey(fromAddress)
                .orElseThrow(() -> new IllegalArgumentException("Unknown derived address: " + fromAddress));

        Account sender = derivationService.derive(
                derivedAccount.getAccount(),
                derivedAccount.getChange(),
                derivedAccount.getIndex());

        Transaction transaction = new Transaction();
        transaction.addInstruction(SystemProgram.transfer(fromPublicKey, toPublicKey, lamports));

        try {
            RpcApi api = rpcClient.getApi();
            LOGGER.info(() -> "Submitting SOL transfer from " + fromAddress + " to " + toAddress
                    + " for " + amount + " SOL (" + lamports + " lamports).");
            return api.sendTransaction(transaction, sender);
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC transfer call failed", e);
            throw new IllegalStateException("Failed to transfer SOL via Solana RPC", e);
        }
    }

    @Override
    public BigDecimal getTokenBalance(String base58Address, String tokenMintAddress) {
        PublicKey ownerPublicKey = parseRequiredPublicKey(base58Address, "Owner");
        PublicKey mintPublicKey = parseRequiredPublicKey(tokenMintAddress, "Token mint");

        try {
            RpcApi api = rpcClient.getApi();
            LOGGER.info(() -> "Requesting token balance for owner " + base58Address
                    + " and mint " + tokenMintAddress + " via endpoint "
                    + SolanaApplicationContext.getConfig().getSolanaRpcUrl());
            Map<String, Object> filter = Map.of("mint", mintPublicKey.toBase58());
            Map<String, Object> config = Map.of("encoding", "jsonParsed");
            TokenAccountInfo tokenAccounts = api.getTokenAccountsByOwner(ownerPublicKey, filter, config);

            if (tokenAccounts == null || tokenAccounts.getValue() == null || tokenAccounts.getValue().isEmpty()) {
                throw new IllegalArgumentException("No token account found for owner and mint");
            }

            TokenAccountInfo.Value accountValue = tokenAccounts.getValue().get(0);
            BigDecimal balance = extractTokenBalance(accountValue);
            LOGGER.info(() -> "Received token balance: " + balance);
            return balance;
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC token balance call failed", e);
            throw new IllegalStateException("Failed to fetch token balance from Solana RPC", e);
        }
    }

    private void validateLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label must not be null or blank");
        }
    }

    private PublicKey parseRequiredPublicKey(String address, String label) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException(label + " address must not be null or blank");
        }
        try {
            return new PublicKey(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " address is not a valid base58-encoded public key", e);
        }
    }

    private BigDecimal extractTokenBalance(TokenAccountInfo.Value accountValue) {
        if (accountValue == null) {
            throw new IllegalArgumentException("Token account details are missing");
        }

        TokenResultObjects.Value value = accountValue.getAccount();
        if (value == null || value.getData() == null || value.getData().getParsed() == null
                || value.getData().getParsed().getInfo() == null
                || value.getData().getParsed().getInfo().getTokenAmount() == null) {
            throw new IllegalStateException("Token account data is incomplete");
        }

        TokenResultObjects.TokenAmountInfo tokenAmount = value.getData().getParsed().getInfo().getTokenAmount();
        String uiAmountString = tokenAmount.getUiAmountString();
        if (uiAmountString != null && !uiAmountString.isBlank()) {
            return new BigDecimal(uiAmountString);
        }

        String rawAmount = tokenAmount.getAmount();
        if (rawAmount != null && !rawAmount.isBlank()) {
            return new BigDecimal(rawAmount).movePointLeft(tokenAmount.getDecimals());
        }

        Double uiAmount = tokenAmount.getUiAmount();
        if (uiAmount != null) {
            return BigDecimal.valueOf(uiAmount);
        }

        throw new IllegalStateException("Token amount details are missing");
    }

    private long validateAndConvertAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        BigDecimal normalizedAmount = amount.stripTrailingZeros();
        if (normalizedAmount.scale() > 9) {
            throw new IllegalArgumentException("Amount must not have more than 9 decimal places");
        }

        BigDecimal lamportsDecimal = amount.multiply(LAMPORTS_PER_SOL);
        try {
            return lamportsDecimal.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must be convertible to lamports without rounding", e);
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
