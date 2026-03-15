package com.familyhub.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // Key is padded/truncated to 32 bytes internally for AES-256
        encryptionService = new TokenEncryptionService("test-encryption-key-32-chars-ok!!");
    }

    @Test
    void encryptThenDecrypt_returnsOriginal() {
        String original = "ya29.a0AfH6SMBx_some_access_token_value";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_producesDifferentCiphertext() {
        String input1 = "token-one";
        String input2 = "token-two";

        String encrypted1 = encryptionService.encrypt(input1);
        String encrypted2 = encryptionService.encrypt(input2);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void encrypt_sameInput_producesDifferentCiphertextEachTime() {
        String input = "same-token";
        String encrypted1 = encryptionService.encrypt(input);
        String encrypted2 = encryptionService.encrypt(input);

        // Due to random IV, same plaintext → different ciphertext
        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // But both decrypt to the same value
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(input);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(input);
    }

    @Test
    void encryptedValue_isNotPlaintext() {
        String original = "my-secret-token";
        String encrypted = encryptionService.encrypt(original);

        assertThat(encrypted).doesNotContain(original);
    }
}
