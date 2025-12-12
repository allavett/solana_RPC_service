package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.wallet.DerivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolanajWalletServiceIntegrationTest {

    private static final String TEST_MNEMONIC = "urge pulp usage sister evidence arrest palm math please chief egg abuse";

    @Mock
    private RpcClient rpcClient;

    @Mock
    private RpcApi rpcApi;

    private InMemoryDerivedAccountRepository repository;
    private InMemoryKeyStorage keyStorage;
    private SolanajWalletService walletService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(rpcClient.getApi()).thenReturn(rpcApi);
        repository = new InMemoryDerivedAccountRepository();
        keyStorage = new InMemoryKeyStorage();
        walletService = new SolanajWalletService(rpcClient, new DerivationService(TEST_MNEMONIC), repository, keyStorage);
    }

    @Test
    void derivesAndStoresSequentialAccountsEndToEnd() throws RpcException {
        when(rpcApi.getBalance(any(PublicKey.class))).thenReturn(0L);

        String firstPubKey = walletService.getNewAddress("first");
        String secondPubKey = walletService.getNewAddress("second");
        String thirdPubKey = walletService.getNewAddress("third");

        DerivedAccount first = repository.findByPublicKey(firstPubKey).orElseThrow();
        DerivedAccount second = repository.findByLabel("second").orElseThrow();
        DerivedAccount third = repository.findByPublicKey(thirdPubKey).orElseThrow();

        assertEquals(0, first.getIndex());
        assertEquals(1, second.getIndex());
        assertEquals(2, third.getIndex());
        assertEquals(firstPubKey, first.getPublicKey());
        assertEquals(secondPubKey, second.getPublicKey());
        assertEquals(thirdPubKey, third.getPublicKey());

        List<DerivedAccount> accounts = walletService.listAccounts();
        assertEquals(3, accounts.size());
        assertTrue(accounts.contains(second));

        BigDecimal balance = walletService.getBalanceByLabel("first");
        assertEquals(new BigDecimal("0.000000000"), balance);

        ArgumentCaptor<PublicKey> keyCaptor = ArgumentCaptor.forClass(PublicKey.class);
        verify(rpcApi, times(1)).getBalance(keyCaptor.capture());
        assertEquals(firstPubKey, keyCaptor.getValue().toBase58());
    }
}
