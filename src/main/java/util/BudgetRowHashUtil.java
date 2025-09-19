package util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for computing a SHA-256 hash for BudgetRow/BudgetTransaction.
 */
public class BudgetRowHashUtil {
    /**
     * Computes a SHA-256 hash from transaction fields.
     * @param fields all relevant fields for uniqueness
     * @return hash string
     */
    public static String computeTransactionHash(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (String field : fields) {
                sb.append(field == null ? "null" : field.trim()).append("|");
            }
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute transaction hash", e);
        }
    }
}