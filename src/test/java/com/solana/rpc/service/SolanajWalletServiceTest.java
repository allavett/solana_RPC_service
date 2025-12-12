package com.solana.rpc.service;

import com.solana.rpc.model.DerivedAccount;
import com.solana.rpc.wallet.DerivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolanajWalletServiceTest {

    private static final String TEST_MNEMONIC = "urge pulp usage sister evidence arrest palm math please chief egg abuse";

    private SolanajWalletService walletService;
    private DerivationService derivationService;
    private DerivedAccountRepository accountRepository;

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
        walletService = new SolanajWalletService(rpcClient, derivationService, accountRepository);
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
    void getNewAddressRejectsMissingLabel() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getNewAddress(null));
        assertThrows(IllegalArgumentException.class, () -> walletService.getNewAddress(""));
    }
}
