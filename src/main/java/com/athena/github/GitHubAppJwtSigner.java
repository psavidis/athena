package com.athena.github;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.math.BigInteger;
import java.security.spec.RSAPrivateCrtKeySpec;

/**
 * Signs the short-lived RS256 JWT GitHub requires for App-level API calls
 * (https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app).
 * Hand-rolled rather than pulling in a JWT library: the token shape needed
 * here is fixed and tiny (three base64url segments, one RSA signature).
 */
class GitHubAppJwtSigner {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();

    private GitHubAppJwtSigner() {
    }

    static String sign(String appId, String privateKeyPem) {
        try {
            Instant now = Instant.now();
            String header = json("alg", "RS256", "typ", "JWT");
            String payload = JSON.createObjectNode()
                    .put("iat", now.minusSeconds(60).getEpochSecond())
                    .put("exp", now.plusSeconds(600).getEpochSecond())
                    .put("iss", appId)
                    .toString();

            String signingInput = encode(header) + "." + encode(payload);
            byte[] signatureBytes = signRs256(signingInput.getBytes(StandardCharsets.US_ASCII), privateKeyPem);
            return signingInput + "." + BASE64_URL.encodeToString(signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new GitHubAuthenticationException("Could not sign GitHub App JWT: " + e.getMessage(), e);
        }
    }

    private static byte[] signRs256(byte[] signingInput, String privateKeyPem) throws GeneralSecurityException {
        PrivateKey privateKey = parsePrivateKey(privateKeyPem);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput);
        return signature.sign();
    }

    private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        // GitHub App keys are distributed as PKCS#1 ("RSA PRIVATE KEY"); the JDK's
        // KeyFactory only accepts PKCS#8, so PKCS#1 DER is unwrapped by hand first.
        return keyFactory.generatePrivate(pkcs1 ? pkcs1ToKeySpec(keyBytes) : new PKCS8EncodedKeySpec(keyBytes));
    }

    private static RSAPrivateCrtKeySpec pkcs1ToKeySpec(byte[] pkcs1Der) {
        Pkcs1Reader reader = new Pkcs1Reader(pkcs1Der);
        reader.readSequenceHeader();
        reader.readInteger();
        BigInteger modulus = reader.readInteger();
        BigInteger publicExponent = reader.readInteger();
        BigInteger privateExponent = reader.readInteger();
        BigInteger primeP = reader.readInteger();
        BigInteger primeQ = reader.readInteger();
        BigInteger primeExponentP = reader.readInteger();
        BigInteger primeExponentQ = reader.readInteger();
        BigInteger crtCoefficient = reader.readInteger();
        return new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent, primeP, primeQ,
                primeExponentP, primeExponentQ, crtCoefficient);
    }

    /** Minimal DER reader for the fixed SEQUENCE-of-INTEGERs shape of a PKCS#1 RSA private key. */
    private static final class Pkcs1Reader {
        private final byte[] der;
        private int position;

        Pkcs1Reader(byte[] der) {
            this.der = der;
        }

        void readSequenceHeader() {
            expectTag(0x30);
            readLength();
        }

        BigInteger readInteger() {
            expectTag(0x02);
            int length = readLength();
            byte[] value = java.util.Arrays.copyOfRange(der, position, position + length);
            position += length;
            return new BigInteger(value);
        }

        private void expectTag(int expected) {
            int tag = der[position++] & 0xFF;
            if (tag != expected) {
                throw new IllegalArgumentException("Malformed PKCS#1 key: expected DER tag " + expected);
            }
        }

        private int readLength() {
            int first = der[position++] & 0xFF;
            if ((first & 0x80) == 0) {
                return first;
            }
            int numBytes = first & 0x7F;
            int length = 0;
            for (int i = 0; i < numBytes; i++) {
                length = (length << 8) | (der[position++] & 0xFF);
            }
            return length;
        }
    }

    private static String json(String... keyValues) {
        var node = JSON.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.put(keyValues[i], keyValues[i + 1]);
        }
        return node.toString();
    }

    private static String encode(String value) {
        return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
