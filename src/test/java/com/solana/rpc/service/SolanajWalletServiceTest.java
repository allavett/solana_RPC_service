package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.wallet.DerivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.ConfirmedTransaction;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.RecentPrioritizationFees;
import org.p2p.solanaj.rpc.types.SimulatedTransaction;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.rpc.types.TokenResultObjects;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolanajWalletServiceTest {

    private static final String TEST_MNEMONIC = "urge pulp usage sister evidence arrest palm math please chief egg abuse";

    private SolanajWalletService walletService;
    private DerivationService derivationService;
    private DerivedAccountRepository accountRepository;
    private InMemoryKeyStorage keyStorage;

    @Mock
    private RpcClient rpcClient;

    @Mock
    private RpcApi rpcApi;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(rpcClient.getApi()).thenReturn(rpcApi);
        derivationService = new DerivationService(TEST_MNEMONIC);
        accountRepository = new InMemoryDerivedAccountRepository();
        keyStorage = new InMemoryKeyStorage();
        walletService = new SolanajWalletService(rpcClient, derivationService, accountRepository, keyStorage);
    }

    @Test
    void getNewAddressUsesNextIndexAndPersistsMetadata() {
        String first = walletService.getNewAddress("primary");
        String second = walletService.getNewAddress("secondary");

        assertEquals("2bahaF9qfc6pE5DJCKQ7AcZF1nXx5Jvf4NwkQib8uwbL", first);
        assertEquals("9LCBeEKbr17HV3Us8cWR7JrnNP6tLK6QDFtMv8RevjP1", second);

        DerivedAccount primary = accountRepository.findByLabel("primary").orElseThrow();
        DerivedAccount secondary = accountRepository.findByLabel("secondary").orElseThrow();

        assertEquals(0, primary.getIndex());
        assertEquals(1, secondary.getIndex());

        List<DerivedAccount> accounts = walletService.listAccounts();
        assertEquals(2, accounts.size());
        assertEquals(2, keyStorage.getAccounts().size());
    }

    @Test
    void getNewAddressWithoutLabelAutoGeneratesLabel() {
        String address = walletService.getNewAddress();

        DerivedAccount account = accountRepository.findByLabel("account-0").orElseThrow();
        assertEquals(address, account.getPublicKey());
        assertEquals(0, account.getIndex());
        assertEquals(1, keyStorage.getAccounts().size());
    }

    @Test
    void getBalanceReturnsConvertedSolValue() throws RpcException {
        when(rpcApi.getBalance(any(PublicKey.class))).thenReturn(2_500_000_000L);

        BigDecimal balance = walletService.getBalance("11111111111111111111111111111111");

        assertEquals(new BigDecimal("2.500000000"), balance);
        verify(rpcApi).getBalance(any(PublicKey.class));
    }

    @Test
    void getBalanceByLabelLooksUpPublicKey() throws RpcException {
        String label = "labeled";
        String address = walletService.getNewAddress(label);
        when(rpcApi.getBalance(any(PublicKey.class))).thenReturn(1_000_000_000L);

        BigDecimal balance = walletService.getBalanceByLabel(label);

        assertEquals(new BigDecimal("1.000000000"), balance);
        verify(rpcApi).getBalance(new PublicKey(address));
    }

    @Test
    void getBalanceRejectsBlankAddress() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getBalance("  "));
    }

    @Test
    void getBalanceRejectsInvalidBase58() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getBalance("not-base58"));
    }

    @Test
    void getBalanceByLabelRejectsUnknownLabel() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getBalanceByLabel("missing"));
    }

    @Test
    void getBalanceByLabelRejectsBlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getBalanceByLabel(" "));
    }

    @Test
    void getTransactionFeeReturnsConvertedSolFee() throws RpcException {
        ConfirmedTransaction transaction = org.mockito.Mockito.mock(ConfirmedTransaction.class);
        ConfirmedTransaction.Meta meta = org.mockito.Mockito.mock(ConfirmedTransaction.Meta.class);

        when(rpcApi.getTransaction("5N1TH8iYamq6WekKQZqygZ5q9U4fK9fE7eY1B2VotS1Y9z9WohV8AhWnM9D5HHu7HaqLvq1ArM4gZgG5EoF7nuh2"))
                .thenReturn(transaction);
        when(transaction.getMeta()).thenReturn(meta);
        when(meta.getFee()).thenReturn(5_000L);

        BigDecimal fee = walletService.getTransactionFee(
                "5N1TH8iYamq6WekKQZqygZ5q9U4fK9fE7eY1B2VotS1Y9z9WohV8AhWnM9D5HHu7HaqLvq1ArM4gZgG5EoF7nuh2");

        assertEquals(new BigDecimal("0.000005000"), fee);
        verify(rpcApi).getTransaction(any(String.class));
    }

    @Test
    void getTransactionFeeRejectsBlankHash() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getTransactionFee(" "));
    }

    @Test
    void getTransactionFeeRejectsInvalidHash() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getTransactionFee("not-base58"));
    }

    @Test
    void getTransactionFeeRejectsMissingTransaction() throws RpcException {
        when(rpcApi.getTransaction(any(String.class))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> walletService.getTransactionFee(
                "5N1TH8iYamq6WekKQZqygZ5q9U4fK9fE7eY1B2VotS1Y9z9WohV8AhWnM9D5HHu7HaqLvq1ArM4gZgG5EoF7nuh2"));
    }

    @Test
    void getTransactionFeeRejectsMissingMetadata() throws RpcException {
        ConfirmedTransaction transaction = org.mockito.Mockito.mock(ConfirmedTransaction.class);
        when(rpcApi.getTransaction(any(String.class))).thenReturn(transaction);
        when(transaction.getMeta()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> walletService.getTransactionFee(
                "5N1TH8iYamq6WekKQZqygZ5q9U4fK9fE7eY1B2VotS1Y9z9WohV8AhWnM9D5HHu7HaqLvq1ArM4gZgG5EoF7nuh2"));
    }

    @Test
    void getEstimatedTransactionFeeCombinesBaseAndPriorityFee() throws Exception {
        Transaction transaction = new Transaction();
        transaction.addInstruction(SystemProgram.transfer(
                new PublicKey("11111111111111111111111111111111"),
                new PublicKey("11111111111111111111111111111111"),
                1_000L));
        transaction.setRecentBlockHash("11111111111111111111111111111111");
        transaction.sign(new Account());

        SimulatedTransaction simulatedTransaction = new SimulatedTransaction();
        SimulatedTransaction.Value value = new SimulatedTransaction.Value();
        setField(value, "logs", List.of("Program 11111111111111111111111111111111 consumed 2500 of 200000 compute units"));
        setField(simulatedTransaction, "value", value);

        when(rpcApi.simulateTransaction(any(String.class), anyList())).thenReturn(simulatedTransaction);
        when(rpcApi.getFeeForMessage(any(String.class))).thenReturn(5_000L);
        RecentPrioritizationFees lowFeeEstimate = new RecentPrioritizationFees(Map.of(
                "slot", 1L,
                "prioritizationFee", 1_000L));
        RecentPrioritizationFees midFeeEstimate = new RecentPrioritizationFees(Map.of(
                "slot", 2L,
                "prioritizationFee", 2_000L));
        RecentPrioritizationFees highFeeEstimate = new RecentPrioritizationFees(Map.of(
                "slot", 3L,
                "prioritizationFee", 3_000L));
        when(rpcApi.getRecentPrioritizationFees(anyList()))
                .thenReturn(List.of(highFeeEstimate, lowFeeEstimate, midFeeEstimate));

        BigDecimal fee = walletService.getEstimatedTransactionFee(transaction);

        assertEquals(new BigDecimal("0.000005005"), fee);
        verify(rpcApi).simulateTransaction(any(String.class), anyList());
        verify(rpcApi).getFeeForMessage(any(String.class));
        verify(rpcApi).getRecentPrioritizationFees(anyList());
    }

    @Test
    void getEstimatedTransactionFeeRejectsNullTransaction() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getEstimatedTransactionFee(null));
    }

    @Test
    void getEstimatedTransactionFeeRejectsEmptyTransaction() {
        Transaction transaction = new Transaction();

        assertThrows(IllegalArgumentException.class, () -> walletService.getEstimatedTransactionFee(transaction));
    }

    @Test
    void getTokenBalanceReturnsConvertedTokenBalance() throws RpcException {
        TokenAccountInfo tokenAccountInfo = org.mockito.Mockito.mock(TokenAccountInfo.class);
        TokenAccountInfo.Value value = org.mockito.Mockito.mock(TokenAccountInfo.Value.class);
        TokenResultObjects.Value accountValue = org.mockito.Mockito.mock(TokenResultObjects.Value.class);
        TokenResultObjects.Data data = org.mockito.Mockito.mock(TokenResultObjects.Data.class);
        TokenResultObjects.ParsedData parsedData = org.mockito.Mockito.mock(TokenResultObjects.ParsedData.class);
        TokenResultObjects.TokenInfo tokenInfo = org.mockito.Mockito.mock(TokenResultObjects.TokenInfo.class);
        TokenResultObjects.TokenAmountInfo tokenAmount = org.mockito.Mockito.mock(TokenResultObjects.TokenAmountInfo.class);

        when(tokenAccountInfo.getValue()).thenReturn(List.of(value));
        when(value.getAccount()).thenReturn(accountValue);
        when(accountValue.getData()).thenReturn(data);
        when(data.getParsed()).thenReturn(parsedData);
        when(parsedData.getInfo()).thenReturn(tokenInfo);
        when(tokenInfo.getTokenAmount()).thenReturn(tokenAmount);
        when(tokenAmount.getUiAmountString()).thenReturn(null);
        when(tokenAmount.getAmount()).thenReturn("1500000");
        when(tokenAmount.getDecimals()).thenReturn(6);

        when(rpcApi.getTokenAccountsByOwner(any(PublicKey.class), anyMap(), anyMap()))
                .thenReturn(tokenAccountInfo);

        BigDecimal balance = walletService.getTokenBalance(
                "11111111111111111111111111111111",
                "So11111111111111111111111111111111111111112");

        assertEquals(0, new BigDecimal("1.5").compareTo(balance));
        verify(rpcApi).getTokenAccountsByOwner(any(PublicKey.class), anyMap(), anyMap());
    }

    @Test
    void getTokenBalanceRejectsMissingTokenAccount() throws RpcException {
        TokenAccountInfo tokenAccountInfo = org.mockito.Mockito.mock(TokenAccountInfo.class);
        when(tokenAccountInfo.getValue()).thenReturn(List.of());
        when(rpcApi.getTokenAccountsByOwner(any(PublicKey.class), anyMap(), anyMap()))
                .thenReturn(tokenAccountInfo);

        assertThrows(IllegalArgumentException.class, () -> walletService.getTokenBalance(
                "11111111111111111111111111111111",
                "So11111111111111111111111111111111111111112"));
    }

    @Test
    void getTokenBalanceRejectsInvalidAddresses() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getTokenBalance(
                "not-base58",
                "So11111111111111111111111111111111111111112"));
        assertThrows(IllegalArgumentException.class, () -> walletService.getTokenBalance(
                "11111111111111111111111111111111",
                "not-base58"));
    }

    @Test
    void getNewAddressRejectsMissingLabel() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getNewAddress(null));
        assertThrows(IllegalArgumentException.class, () -> walletService.getNewAddress(""));
    }

    @Test
    void transferSolSubmitsTransactionForDerivedAccount() throws RpcException {
        String sender = walletService.getNewAddress("sender");
        when(rpcApi.sendTransaction(any(Transaction.class), any(Account.class))).thenReturn("signature");

        String signature = walletService.transferSol(sender, "11111111111111111111111111111111", new BigDecimal("1.5"));

        assertEquals("signature", signature);
        verify(rpcApi).sendTransaction(any(Transaction.class), any(Account.class));
    }

    @Test
    void transferSolRejectsUnknownSender() {
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSol(
                "11111111111111111111111111111111",
                "9LCBeEKbr17HV3Us8cWR7JrnNP6tLK6QDFtMv8RevjP1",
                new BigDecimal("1")));
    }

    @Test
    void transferSolRejectsInvalidAddresses() {
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSol(
                "not-base58",
                "11111111111111111111111111111111",
                new BigDecimal("1")));
        String sender = walletService.getNewAddress("valid-sender");
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSol(
                sender,
                "not-base58",
                new BigDecimal("1")));
    }

    @Test
    void transferSolRejectsInvalidAmount() {
        String sender = walletService.getNewAddress("amount-sender");

        assertThrows(IllegalArgumentException.class, () -> walletService.transferSol(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSol(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("0")));
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSol(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("0.0000000001")));
    }

    @Test
    void transferSolTokenSubmitsTransactionForDerivedAccount() throws RpcException {
        String sender = walletService.getNewAddress("token-sender");
        TokenResultObjects.TokenAmountInfo supplyInfo = org.mockito.Mockito.mock(TokenResultObjects.TokenAmountInfo.class);
        AccountInfo accountInfo = org.mockito.Mockito.mock(AccountInfo.class);
        AccountInfo.Value value = org.mockito.Mockito.mock(AccountInfo.Value.class);

        when(supplyInfo.getDecimals()).thenReturn(6);
        when(rpcApi.getTokenSupply(any(PublicKey.class))).thenReturn(supplyInfo);
        when(accountInfo.getValue()).thenReturn(value);
        when(rpcApi.getAccountInfo(any(PublicKey.class))).thenReturn(accountInfo);
        when(rpcApi.sendTransaction(any(Transaction.class), any(Account.class))).thenReturn("token-signature");

        String signature = walletService.transferSolToken(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("1.5"),
                "So11111111111111111111111111111111111111112");

        assertEquals("token-signature", signature);
        verify(rpcApi).sendTransaction(any(Transaction.class), any(Account.class));
    }

    @Test
    void transferSolTokenRejectsUnknownSender() {
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                "11111111111111111111111111111111",
                "9LCBeEKbr17HV3Us8cWR7JrnNP6tLK6QDFtMv8RevjP1",
                new BigDecimal("1"),
                "So11111111111111111111111111111111111111112"));
    }

    @Test
    void transferSolTokenRejectsInvalidAddresses() {
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                "not-base58",
                "11111111111111111111111111111111",
                new BigDecimal("1"),
                "So11111111111111111111111111111111111111112"));
        String sender = walletService.getNewAddress("valid-token-sender");
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                sender,
                "not-base58",
                new BigDecimal("1"),
                "So11111111111111111111111111111111111111112"));
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("1"),
                "not-base58"));
    }

    @Test
    void transferSolTokenRejectsInvalidAmount() throws RpcException {
        String sender = walletService.getNewAddress("token-amount-sender");
        TokenResultObjects.TokenAmountInfo supplyInfo = org.mockito.Mockito.mock(TokenResultObjects.TokenAmountInfo.class);
        when(supplyInfo.getDecimals()).thenReturn(6);
        when(rpcApi.getTokenSupply(any(PublicKey.class))).thenReturn(supplyInfo);

        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("-1"),
                "So11111111111111111111111111111111111111112"));
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("0"),
                "So11111111111111111111111111111111111111112"));
        assertThrows(IllegalArgumentException.class, () -> walletService.transferSolToken(
                sender,
                "11111111111111111111111111111111",
                new BigDecimal("0.0000001"),
                "So11111111111111111111111111111111111111112"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
