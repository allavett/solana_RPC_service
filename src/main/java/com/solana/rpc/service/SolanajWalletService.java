package com.solana.rpc.service;

import com.solana.rpc.config.SolanaApplicationContext;
import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.wallet.DerivationService;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.Message;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.AssociatedTokenProgram;
import org.p2p.solanaj.programs.ComputeBudgetProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.programs.TokenProgram;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.ConfirmedTransaction;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.LatestBlockhash;
import org.p2p.solanaj.rpc.types.RecentPrioritizationFees;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.rpc.types.TokenResultObjects;
import org.p2p.solanaj.utils.Base58;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of {@link SolanaWalletService} backed by the Solanaj RPC client and deterministic key derivation.
 */
public class SolanajWalletService implements SolanaWalletService {

    private static final Logger LOGGER = Logger.getLogger(SolanajWalletService.class.getName());
    private static final BigDecimal LAMPORTS_PER_SOL = new BigDecimal("1000000000");
    private static final long MAX_PRIORITY_FEE_LAMPORTS = 5_000L;
    private static final long DEFAULT_COMPUTE_UNIT_LIMIT = 100_000L;
    private static final long REQUIRED_FEE_PAYER_BALANCE_LAMPORTS = 10_000L;
    private static final int DEFAULT_ACCOUNT = 0;
    private static final int DEFAULT_CHANGE = 0;

    private final RpcClient rpcClient;
    private final DerivationService derivationService;
    private final DerivedAccountRepository accountRepository;
    private final KeyStorage keyStorage;
    private final DerivedAccount defaultFeePayer;

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
        this.defaultFeePayer = null;
    }

    public SolanajWalletService(RpcClient rpcClient, DerivationService derivationService,
                                DerivedAccountRepository accountRepository, KeyStorage keyStorage,
                                String defaultFeePayerAddress) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient must not be null");
        this.derivationService = Objects.requireNonNull(derivationService, "derivationService must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.keyStorage = Objects.requireNonNull(keyStorage, "keyStorage must not be null");
        if (defaultFeePayerAddress == null || defaultFeePayerAddress.isBlank()) {
            this.defaultFeePayer = null;
        } else {
            this.defaultFeePayer = accountRepository.findByPublicKey(defaultFeePayerAddress)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Default fee payer must exist in account repository: " + defaultFeePayerAddress));
        }
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

        Account sender = resolveFeePayer(fromAddress);

        try {
            RpcApi api = rpcClient.getApi();
            ensureFeePayerBalance(sender, api);
            Transaction transaction = createSolTransferTransaction(fromPublicKey, toPublicKey, lamports);
            addPrioritizationFeeInstruction(transaction, api, sender);
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

    @Override
    public String transferSolToken(String fromAddress, String toAddress, BigDecimal amount, String tokenAddress) {
        PublicKey fromPublicKey = parseRequiredPublicKey(fromAddress, "Sender");
        PublicKey toPublicKey = parseRequiredPublicKey(toAddress, "Recipient");
        PublicKey mintPublicKey = parseRequiredPublicKey(tokenAddress, "Token mint");

        Account sender = resolveFeePayer(fromAddress);

        try {
            RpcApi api = rpcClient.getApi();
            ensureFeePayerBalance(sender, api);
            TokenTransferPlan plan = buildTokenTransferPlan(fromPublicKey, toPublicKey, mintPublicKey, amount, api);
            Transaction transaction = plan.transaction();

            LOGGER.info(() -> "Submitting SPL token transfer from " + fromAddress + " to " + toAddress
                    + " for " + amount + " tokens (base units=" + plan.baseUnits()
                    + ", decimals=" + plan.decimals() + ").");

            addPrioritizationFeeInstruction(transaction, api, sender);
            return api.sendTransaction(transaction, sender);
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC token transfer call failed", e);
            throw new IllegalStateException("Failed to transfer SPL token via Solana RPC", e);
        }
    }

    @Override
    public BigDecimal getTransactionFee(String transactionHash) {
        validateTransactionHash(transactionHash);

        try {
            RpcApi api = rpcClient.getApi();
            LOGGER.info(() -> "Requesting transaction fee for signature " + transactionHash
                    + " via endpoint " + SolanaApplicationContext.getConfig().getSolanaRpcUrl());
            ConfirmedTransaction transaction = api.getTransaction(transactionHash);
            if (transaction == null || transaction.getMeta() == null) {
                throw new IllegalStateException("Transaction metadata is unavailable for signature: " + transactionHash);
            }
            long feeLamports = transaction.getMeta().getFee();
            return BigDecimal.valueOf(feeLamports).divide(LAMPORTS_PER_SOL, 9, RoundingMode.DOWN);
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC getTransaction call failed", e);
            throw new IllegalStateException("Failed to fetch transaction fee from Solana RPC", e);
        }
    }

    @Override
    public BigDecimal getEstimatedTransactionFee(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction must not be null");
        }

        Message message = extractMessage(transaction);
        List<TransactionInstruction> instructions = extractInstructions(message);
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("Transaction must contain at least one instruction");
        }

        try {
            RpcApi api = rpcClient.getApi();
            ensureRecentBlockhash(transaction, message, api);
            Account feePayer = extractFeePayer(message);
            if (feePayer == null) {
                throw new IllegalStateException("Fee payer is required to estimate transaction fees");
            }
            transaction.sign(feePayer);

            ensureSerializedMessage(transaction, message);
            String serializedTransaction = Base64.getEncoder().encodeToString(transaction.serialize());
            List<PublicKey> accountKeys = extractAccountKeys(message);

            SimulatedTransaction simulatedTransaction = api.simulateTransaction(serializedTransaction, accountKeys);
            long computeUnits = extractComputeUnits(simulatedTransaction);

            String messageBase64 = Base64.getEncoder().encodeToString(message.serialize());
            Long baseFeeLamports = api.getFeeForMessage(messageBase64);
            if (baseFeeLamports == null) {
                throw new IllegalStateException("Base fee is unavailable for the provided transaction message");
            }

            long prioritizationFeeMicroLamports = fetchPrioritizationFeeMicroLamports(api, accountKeys);

            BigDecimal prioritizationLamports = BigDecimal.valueOf(prioritizationFeeMicroLamports)
                    .multiply(BigDecimal.valueOf(computeUnits))
                    .divide(BigDecimal.valueOf(1_000_000), 0, RoundingMode.DOWN);
            if (prioritizationLamports.compareTo(BigDecimal.valueOf(MAX_PRIORITY_FEE_LAMPORTS)) > 0) {
                prioritizationLamports = BigDecimal.valueOf(MAX_PRIORITY_FEE_LAMPORTS);
            }

            BigDecimal totalLamports = BigDecimal.valueOf(baseFeeLamports).add(prioritizationLamports);
            return totalLamports.divide(LAMPORTS_PER_SOL, 9, RoundingMode.DOWN);
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC getEstimatedTransactionFee call failed", e);
            throw new IllegalStateException("Failed to estimate transaction fee via Solana RPC", e);
        }
    }

    public Transaction buildSolTransferTransaction(String fromAddress, String toAddress, BigDecimal amount) {
        PublicKey fromPublicKey = parseRequiredPublicKey(fromAddress, "Sender");
        PublicKey toPublicKey = parseRequiredPublicKey(toAddress, "Recipient");
        long lamports = validateAndConvertAmount(amount);
        Transaction transaction = createSolTransferTransaction(fromPublicKey, toPublicKey, lamports);
        ensureFeePayer(extractMessage(transaction), resolveFeePayer(fromAddress));
        return transaction;
    }

    public Transaction buildTokenTransferTransaction(String fromAddress, String toAddress, BigDecimal amount, String tokenAddress) {
        PublicKey fromPublicKey = parseRequiredPublicKey(fromAddress, "Sender");
        PublicKey toPublicKey = parseRequiredPublicKey(toAddress, "Recipient");
        PublicKey mintPublicKey = parseRequiredPublicKey(tokenAddress, "Token mint");

        try {
            RpcApi api = rpcClient.getApi();
            TokenTransferPlan plan = buildTokenTransferPlan(fromPublicKey, toPublicKey, mintPublicKey, amount, api);
            Transaction transaction = plan.transaction();
            ensureFeePayer(extractMessage(transaction), resolveFeePayer(fromAddress));
            return transaction;
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC token transfer build call failed", e);
            throw new IllegalStateException("Failed to build SPL token transfer transaction", e);
        }
    }

    public String sendPreparedTransaction(String fromAddress, Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction must not be null");
        }

        Account sender = resolveFeePayer(fromAddress);

        try {
            RpcApi api = rpcClient.getApi();
            addPrioritizationFeeInstruction(transaction, api, sender);
            LOGGER.info(() -> "Submitting prepared transaction from " + fromAddress + ".");
            return api.sendTransaction(transaction, sender);
        } catch (RpcException e) {
            LOGGER.log(Level.SEVERE, "RPC sendPreparedTransaction call failed", e);
            throw new IllegalStateException("Failed to send transaction via Solana RPC", e);
        }
    }

    private void validateLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label must not be null or blank");
        }
    }

    private void validateTransactionHash(String transactionHash) {
        if (transactionHash == null || transactionHash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash must not be null or blank");
        }
        try {
            Base58.decode(transactionHash);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Transaction hash is not a valid base58-encoded signature", e);
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

    private int fetchTokenDecimals(RpcApi api, PublicKey mintPublicKey) throws RpcException {
        TokenResultObjects.TokenAmountInfo supplyInfo = api.getTokenSupply(mintPublicKey);
        if (supplyInfo == null) {
            throw new IllegalStateException("Token mint metadata is unavailable for: " + mintPublicKey.toBase58());
        }
        int decimals = supplyInfo.getDecimals();
        if (decimals < 0 || decimals > 255) {
            throw new IllegalArgumentException("Token decimals are out of range: " + decimals);
        }
        return decimals;
    }

    private long validateAndConvertTokenAmount(BigDecimal amount, int decimals) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        BigDecimal normalizedAmount = amount.stripTrailingZeros();
        if (normalizedAmount.scale() > decimals) {
            throw new IllegalArgumentException("Amount must not have more than " + decimals + " decimal places");
        }

        BigDecimal baseUnitsDecimal = amount.movePointRight(decimals);
        try {
            return baseUnitsDecimal.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must be convertible to base units without rounding", e);
        }
    }

    private boolean accountExists(RpcApi api, PublicKey address) throws RpcException {
        AccountInfo accountInfo = api.getAccountInfo(address);
        return accountInfo != null && accountInfo.getValue() != null;
    }

    private PublicKey findAssociatedTokenAddress(PublicKey owner, PublicKey mint) {
        List<byte[]> seeds = List.of(
                owner.toByteArray(),
                TokenProgram.PROGRAM_ID.toByteArray(),
                mint.toByteArray());
        return PublicKey.findProgramAddress(seeds, AssociatedTokenProgram.PROGRAM_ID).getAddress();
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

    private Message extractMessage(Transaction transaction) {
        try {
            Field messageField = Transaction.class.getDeclaredField("message");
            messageField.setAccessible(true);
            Message message = (Message) messageField.get(transaction);
            if (message == null) {
                throw new IllegalStateException("Transaction message is unavailable");
            }
            return message;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to access transaction message", e);
        }
    }

    private List<PublicKey> extractAccountKeys(Message message) {
        try {
            List<AccountMeta> accountMetas = message.getAccountKeys();
            if (accountMetas == null) {
                return List.of();
            }
            return accountMetas.stream().map(AccountMeta::getPublicKey).toList();
        } catch (NullPointerException e) {
            return List.of();
        }
    }

    private Account extractFeePayer(Message message) {
        try {
            Field feePayerField = Message.class.getDeclaredField("feePayer");
            feePayerField.setAccessible(true);
            return (Account) feePayerField.get(message);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to access fee payer on transaction message", e);
        }
    }

    private void addPrioritizationFeeInstruction(Transaction transaction, RpcApi api, Account feePayer) throws RpcException {
        Message message = extractMessage(transaction);
        ensureFeePayer(message, feePayer);
        ensureRecentBlockhash(transaction, message, api);
        Transaction simulation = buildSimulationTransaction(message, feePayer, api);
        Message simulationMessage = extractMessage(simulation);
        String serializedTransaction = Base64.getEncoder().encodeToString(simulation.serialize());
        List<PublicKey> accountKeys = extractAccountKeys(simulationMessage);
        SimulatedTransaction simulatedTransaction = api.simulateTransaction(serializedTransaction, accountKeys);
        long computeUnits = extractComputeUnits(simulatedTransaction);
        long prioritizationFeeMicroLamports = fetchPrioritizationFeeMicroLamports(api, accountKeys);
        List<TransactionInstruction> instructions = extractInstructions(message);
        if (instructions == null) {
            throw new IllegalStateException("Transaction instructions are unavailable");
        }
        long resolvedComputeUnitLimit = computeUnits > 0 ? computeUnits : DEFAULT_COMPUTE_UNIT_LIMIT;
        int computeUnitLimit = resolvedComputeUnitLimit > Integer.MAX_VALUE
                ? (int) DEFAULT_COMPUTE_UNIT_LIMIT
                : (int) resolvedComputeUnitLimit;
        TransactionInstruction computeUnitLimitInstruction = ComputeBudgetProgram.setComputeUnitLimit(computeUnitLimit);
        message.addInstruction(computeUnitLimitInstruction);
        if (prioritizationFeeMicroLamports <= 0) {
            instructions.remove(computeUnitLimitInstruction);
            instructions.add(0, computeUnitLimitInstruction);
            return;
        }
        long maxMicroLamports = computeUnits == 0
                ? 0
                : (MAX_PRIORITY_FEE_LAMPORTS * 1_000_000L) / computeUnits;
        if (maxMicroLamports <= 0) {
            instructions.remove(computeUnitLimitInstruction);
            instructions.add(0, computeUnitLimitInstruction);
            return;
        }
        long boundedFeeMicroLamports = Math.min(prioritizationFeeMicroLamports, maxMicroLamports);
        int feeMicroLamports = boundedFeeMicroLamports > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) boundedFeeMicroLamports;
        TransactionInstruction computeUnitPriceInstruction = ComputeBudgetProgram.setComputeUnitPrice(feeMicroLamports);
        message.addInstruction(computeUnitPriceInstruction);
        instructions.remove(computeUnitLimitInstruction);
        instructions.remove(computeUnitPriceInstruction);
        instructions.add(0, computeUnitLimitInstruction);
        instructions.add(1, computeUnitPriceInstruction);
    }

    private Transaction createSolTransferTransaction(PublicKey fromPublicKey, PublicKey toPublicKey, long lamports) {
        Transaction transaction = new Transaction();
        transaction.addInstruction(SystemProgram.transfer(fromPublicKey, toPublicKey, lamports));
        return transaction;
    }

    private TokenTransferPlan buildTokenTransferPlan(PublicKey fromPublicKey, PublicKey toPublicKey,
                                                     PublicKey mintPublicKey, BigDecimal amount, RpcApi api)
            throws RpcException {
        int decimals = fetchTokenDecimals(api, mintPublicKey);
        long baseUnits = validateAndConvertTokenAmount(amount, decimals);

        PublicKey senderTokenAccount = findAssociatedTokenAddress(fromPublicKey, mintPublicKey);
        PublicKey recipientTokenAccount = findAssociatedTokenAddress(toPublicKey, mintPublicKey);

        Transaction transaction = new Transaction();

        if (!accountExists(api, senderTokenAccount)) {
            transaction.addInstruction(AssociatedTokenProgram.createIdempotent(
                    fromPublicKey, fromPublicKey, mintPublicKey));
        }

        if (!accountExists(api, recipientTokenAccount)) {
            transaction.addInstruction(AssociatedTokenProgram.createIdempotent(
                    fromPublicKey, toPublicKey, mintPublicKey));
        }

        transaction.addInstruction(TokenProgram.transferChecked(
                senderTokenAccount,
                recipientTokenAccount,
                baseUnits,
                (byte) decimals,
                fromPublicKey,
                mintPublicKey));

        return new TokenTransferPlan(transaction, baseUnits, decimals);
    }

    private record TokenTransferPlan(Transaction transaction, long baseUnits, int decimals) {
    }

    private Account resolveFeePayer(String fromAddress) {
        if (defaultFeePayer != null) {
            return derivationService.derive(
                    defaultFeePayer.getAccount(),
                    defaultFeePayer.getChange(),
                    defaultFeePayer.getIndex());
        }
        DerivedAccount derivedAccount = accountRepository.findByPublicKey(fromAddress)
                .orElseThrow(() -> new IllegalArgumentException("Unknown derived address: " + fromAddress));
        return derivationService.derive(
                derivedAccount.getAccount(),
                derivedAccount.getChange(),
                derivedAccount.getIndex());
    }

    private void ensureFeePayerBalance(Account feePayer, RpcApi api) throws RpcException {
        long balance = api.getBalance(feePayer.getPublicKey());
        if (balance < REQUIRED_FEE_PAYER_BALANCE_LAMPORTS) {
            throw new IllegalStateException("Fee payer must have at least "
                    + REQUIRED_FEE_PAYER_BALANCE_LAMPORTS + " lamports to cover fees");
        }
    }

    private Transaction buildSimulationTransaction(Message sourceMessage, Account feePayer, RpcApi api)
            throws RpcException {
        List<TransactionInstruction> sourceInstructions = extractInstructions(sourceMessage);
        if (sourceInstructions == null || sourceInstructions.isEmpty()) {
            throw new IllegalStateException("Simulation requires at least one instruction");
        }

        Transaction simulation = new Transaction();
        for (TransactionInstruction instruction : sourceInstructions) {
            simulation.addInstruction(instruction);
        }
        Message simulationMessage = extractMessage(simulation);
        ensureFeePayer(simulationMessage, feePayer);
        ensureRecentBlockhash(simulation, simulationMessage, api);
        simulation.sign(feePayer);
        ensureSerializedMessage(simulation, simulationMessage);
        return simulation;
    }

    private long fetchPrioritizationFeeMicroLamports(RpcApi api, List<PublicKey> accountKeys) throws RpcException {
        List<RecentPrioritizationFees> prioritizationFees = accountKeys == null || accountKeys.isEmpty()
                ? api.getRecentPrioritizationFees()
                : api.getRecentPrioritizationFees(accountKeys);
        return selectPrioritizationFee(prioritizationFees);
    }

    private void ensureFeePayer(Message message, Account feePayer) {
        if (feePayer == null) {
            throw new IllegalArgumentException("Fee payer must not be null");
        }
        try {
            Field feePayerField = Message.class.getDeclaredField("feePayer");
            feePayerField.setAccessible(true);
            if (feePayerField.get(message) == null) {
                feePayerField.set(message, feePayer);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to set fee payer on transaction message", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<TransactionInstruction> extractInstructions(Message message) {
        try {
            Field instructionsField = Message.class.getDeclaredField("instructions");
            instructionsField.setAccessible(true);
            return (List<TransactionInstruction>) instructionsField.get(message);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to inspect transaction instructions", e);
        }
    }

    private void ensureRecentBlockhash(Transaction transaction, Message message, RpcApi api) throws RpcException {
        try {
            Field blockhashField = Message.class.getDeclaredField("recentBlockhash");
            blockhashField.setAccessible(true);
            String recentBlockhash = (String) blockhashField.get(message);
            if (recentBlockhash != null && !recentBlockhash.isBlank()) {
                return;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to inspect recent blockhash", e);
        }

        LatestBlockhash latestBlockhash = api.getLatestBlockhash();
        if (latestBlockhash == null || latestBlockhash.getValue() == null
                || latestBlockhash.getValue().getBlockhash() == null
                || latestBlockhash.getValue().getBlockhash().isBlank()) {
            throw new IllegalStateException("Latest blockhash is unavailable");
        }
        transaction.setRecentBlockHash(latestBlockhash.getValue().getBlockhash());
    }

    private void ensureSerializedMessage(Transaction transaction, Message message) {
        try {
            Field serializedField = Transaction.class.getDeclaredField("serializedMessage");
            serializedField.setAccessible(true);
            if (serializedField.get(transaction) == null) {
                serializedField.set(transaction, message.serialize());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to serialize transaction message", e);
        }
    }

    private long extractComputeUnits(SimulatedTransaction simulatedTransaction) {
        if (simulatedTransaction == null || simulatedTransaction.getValue() == null
                || simulatedTransaction.getValue().getLogs() == null) {
            return DEFAULT_COMPUTE_UNIT_LIMIT;
        }

        Pattern pattern = Pattern.compile("consumed (\\d+) of (\\d+) compute units");
        OptionalLong maxConsumed = simulatedTransaction.getValue().getLogs().stream()
                .map(pattern::matcher)
                .filter(Matcher::find)
                .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                .max();

        return maxConsumed.orElse(DEFAULT_COMPUTE_UNIT_LIMIT);
    }

    private long selectPrioritizationFee(List<RecentPrioritizationFees> prioritizationFees) {
        if (prioritizationFees == null || prioritizationFees.isEmpty()) {
            return 0L;
        }
        List<Long> fees = prioritizationFees.stream()
                .map(RecentPrioritizationFees::getPrioritizationFee)
                .sorted()
                .toList();
        int size = fees.size();
        if (size % 2 == 1) {
            return fees.get(size / 2);
        }
        long lower = fees.get(size / 2 - 1);
        long upper = fees.get(size / 2);
        return (lower + upper) / 2;
    }
}
