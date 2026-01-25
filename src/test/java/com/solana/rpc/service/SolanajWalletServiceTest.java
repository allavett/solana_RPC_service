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
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.TokenAccountInfo;
import org.p2p.solanaj.rpc.types.TokenResultObjects;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
}
