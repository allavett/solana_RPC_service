package com.solana.rpc.wallet;

import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.utils.bip32.wallet.HdAddress;
import org.p2p.solanaj.utils.bip32.wallet.HdKeyGenerator;
import org.p2p.solanaj.utils.bip32.wallet.SolanaCoin;
import org.p2p.solanaj.utils.TweetNaclFast;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility for deriving Solana keypairs from a BIP39 mnemonic using the standard
 * m/44'/501'/account'/change'/index path structure.
 */
public class DerivationService {

    private static final int PBKDF2_ITERATIONS = 2048;
    private static final int PBKDF2_KEY_LENGTH = 512;

    private final List<String> mnemonicWords;
    private final String passphrase;
    private final HdKeyGenerator hdKeyGenerator;
    private final SolanaCoin solanaCoin;

    /**
     * Create a new derivation service for the provided mnemonic with an empty passphrase.
     *
     * @param mnemonic space-delimited BIP39 mnemonic
     */
    public DerivationService(String mnemonic) {
        this(mnemonic, "");
    }

    /**
     * Create a new derivation service for the provided mnemonic and passphrase.
     *
     * @param mnemonic   space-delimited BIP39 mnemonic
     * @param passphrase optional passphrase, may be blank
     */
    public DerivationService(String mnemonic, String passphrase) {
        Objects.requireNonNull(mnemonic, "mnemonic must not be null");
        Objects.requireNonNull(passphrase, "passphrase must not be null");

        String trimmedMnemonic = mnemonic.trim();
        if (trimmedMnemonic.isEmpty()) {
            throw new IllegalArgumentException("Mnemonic must not be blank");
        }

        List<String> words = Arrays.asList(trimmedMnemonic.split("\\s+"));
        if (words.size() < 12) {
            throw new IllegalArgumentException("Mnemonic must contain at least 12 words");
        }

        this.mnemonicWords = words;
        this.passphrase = passphrase;
        this.hdKeyGenerator = new HdKeyGenerator();
        this.solanaCoin = new SolanaCoin();
    }

    /**
     * Derive a Solana keypair for the given account/change/index tuple using the
     * m/44'/501'/account'/change'/index path.
     *
     * @param account account index (hardened); must be zero or positive
     * @param change  change level (hardened); must be zero or positive
     * @param index   address index (hardened); must be zero or positive
     * @return derived Solana account
     */
    public Account derive(int account, int change, int index) {
        if (account < 0 || change < 0 || index < 0) {
            throw new IllegalArgumentException("Derivation path components must not be negative");
        }

        byte[] seed = mnemonicToSeed(this.mnemonicWords, this.passphrase);

        HdAddress master = hdKeyGenerator.getAddressFromSeed(seed, solanaCoin);
        HdAddress purpose = hdKeyGenerator.getAddress(master, solanaCoin.getPurpose(), solanaCoin.getAlwaysHardened());
        HdAddress coinType = hdKeyGenerator.getAddress(purpose, solanaCoin.getCoinType(), solanaCoin.getAlwaysHardened());
        HdAddress accountNode = hdKeyGenerator.getAddress(coinType, account, solanaCoin.getAlwaysHardened());
        HdAddress changeNode = hdKeyGenerator.getAddress(accountNode, change, solanaCoin.getAlwaysHardened());
        HdAddress indexNode = hdKeyGenerator.getAddress(changeNode, index, solanaCoin.getAlwaysHardened());

        byte[] seed32 = indexNode.getPrivateKey().getPrivateKey();
        TweetNaclFast.Signature.KeyPair keyPair = TweetNaclFast.Signature.keyPair_fromSeed(seed32);
        return new Account(keyPair.getSecretKey());
    }

    /**
     * Derive the base58-encoded public key for the given account/change/index tuple.
     *
     * @param account account index (hardened); must be zero or positive
     * @param change  change level (hardened); must be zero or positive
     * @param index   address index (hardened); must be zero or positive
     * @return base58-encoded public key string
     */
    public String derivePublicKeyBase58(int account, int change, int index) {
        return derive(account, change, index).getPublicKey().toBase58();
    }

    private static byte[] mnemonicToSeed(List<String> words, String passphrase) {
        String sentence = String.join(" ", words);
        String normalizedMnemonic = Normalizer.normalize(sentence, Normalizer.Form.NFKD);
        String normalizedPassphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFKD);
        String salt = "mnemonic" + normalizedPassphrase;

        try {
            PBEKeySpec keySpec = new PBEKeySpec(normalizedMnemonic.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8), PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            return secretKeyFactory.generateSecret(keySpec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive seed from mnemonic", e);
        }
    }
}
