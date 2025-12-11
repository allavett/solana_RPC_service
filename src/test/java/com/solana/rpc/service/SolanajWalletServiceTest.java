package com.solana.rpc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolanajWalletServiceTest {

    private SolanajWalletService walletService;

    @Mock
    private RpcClient rpcClient;

    @Mock
    private RpcApi rpcApi;

    @Mock
    private KeyStorage keyStorage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(rpcClient.getApi()).thenReturn(rpcApi);
        walletService = new SolanajWalletService(rpcClient, keyStorage);
    }

    @Test
    void getNewAddressGeneratesAndPersistsAccount() {
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

        String newAddress = walletService.getNewAddress();

        verify(keyStorage).save(accountCaptor.capture());
        Account capturedAccount = accountCaptor.getValue();

        assertEquals(capturedAccount.getPublicKey().toBase58(), newAddress);
        assertDoesNotThrow(() -> new PublicKey(newAddress));
    }

    @Test
    void getBalanceReturnsConvertedSolValue() throws RpcException {
        when(rpcApi.getBalance(any(PublicKey.class))).thenReturn(2_500_000_000L);

        BigDecimal balance = walletService.getBalance("11111111111111111111111111111111");

        assertEquals(new BigDecimal("2.500000000"), balance);

        ArgumentCaptor<PublicKey> publicKeyCaptor = ArgumentCaptor.forClass(PublicKey.class);
        verify(rpcApi).getBalance(publicKeyCaptor.capture());
        assertEquals("11111111111111111111111111111111", publicKeyCaptor.getValue().toBase58());
    }

    @Test
    void getBalanceRejectsBlankAddress() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getBalance("  "));
    }

    @Test
    void getBalanceWrapsRpcExceptions() throws RpcException {
        when(rpcApi.getBalance(any(PublicKey.class))).thenThrow(new RpcException("rpc unavailable"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                walletService.getBalance("11111111111111111111111111111111"));

        assertTrue(ex.getCause() instanceof RpcException);
    }
}
