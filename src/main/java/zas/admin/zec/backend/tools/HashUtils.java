package zas.admin.zec.backend.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {

    private HashUtils() {}

    /**
     * Returns the SHA-256 hash for the given input string.
     */
    public static String sha256(String input) {
        return hash(input, "SHA-256");
    }

    /**
     * A generic method to hash the input string using the given algorithm
     * (e.g. "SHA-256", "SHA-1", "MD5", etc.).
     *
     * @param input the string to be hashed
     * @param algorithm the name of the algorithm to use
     * @return the hexadecimal hash string
     */
    public static String hash(String input, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error: Unsupported hashing algorithm " + algorithm, e);
        }
    }

    /**
     * Utility to convert a byte array into a hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            // Convert each byte to a 2-digit hex string
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0'); // leading zero
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
