package it.eng.tools.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.encoders.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class FieldEncryptionService {

    private final byte[] key;
    private final byte[] iv;

    public FieldEncryptionService(
            @Value("${application.encryption.key}") String encryptionKey)
            throws NoSuchAlgorithmException {
        try {
            // Generate a consistent 256-bit key from the encryption key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            this.key = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));

            // Generate a consistent IV from the encryption key
            MessageDigest ivDigest = MessageDigest.getInstance("MD5");
            this.iv = ivDigest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }

    public String encrypt(String value) {
        try {
            log.debug("encrypting: {}", value);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new AESEngine()),
                    new PKCS7Padding());

            cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));

            byte[] input = value.getBytes();
            byte[] output = new byte[cipher.getOutputSize(input.length)];

            int processed = cipher.processBytes(input, 0, input.length, output, 0);
            cipher.doFinal(output, processed);
            return Base64.toBase64String(output);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new AESEngine()),
                    new PKCS7Padding());

            cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));

            byte[] input = Base64.decode(encrypted);
            byte[] output = new byte[cipher.getOutputSize(input.length)];

            int processed = cipher.processBytes(input, 0, input.length, output, 0);
            int finalProcessed = cipher.doFinal(output, processed);

            return new String(output, 0, processed + finalProcessed).trim();
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
