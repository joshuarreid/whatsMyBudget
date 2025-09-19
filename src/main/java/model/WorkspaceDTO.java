package model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/**
 * Unified DTO for all workspace state required for cloud sync/backup.
 * Includes all transactions, projections, config, hashes, and metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceDTO implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceDTO.class);

    private List<ProjectedTransaction> projectedTransactions;
    private List<BudgetTransaction> budgetTransactions;
    private LocalCacheState localCacheState;

    // Section hashes (SHA-256, Base64-encoded)
    private String budgetTransactionsHash;
    private String projectionsHash;
    private String localCacheStateHash;

    // Metadata
    private String version;
    private String lastModified; // ISO8601

    /**
     * Computes SHA-256 hash of a JSON-serializable object (section).
     * @param obj object to hash
     * @return Base64-encoded SHA-256 hash, or null on error
     */
    public static String computeSectionHash(Object obj) {
        logger.info("Computing section hash for object: {}", obj == null ? "null" : obj.getClass().getName());
        if (obj == null) return null;
        try {
            ObjectMapper om = new ObjectMapper();
            String json = om.writeValueAsString(obj);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(hash);
            logger.debug("Section hash computed: {}", encoded);
            return encoded;
        } catch (Exception e) {
            logger.error("Error computing section hash: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Updates all section hashes in this WorkspaceDTO.
     */
    public void updateAllSectionHashes() {
        logger.info("Updating all section hashes in WorkspaceDTO.");
        this.budgetTransactionsHash = computeSectionHash(this.budgetTransactions);
        this.projectionsHash = computeSectionHash(this.projectedTransactions);
        this.localCacheStateHash = computeSectionHash(this.localCacheState);
        logger.info("Section hashes updated: budgetTransactionsHash={}, projectionsHash={}, localCacheStateHash={}",
                budgetTransactionsHash, projectionsHash, localCacheStateHash);
    }

    /**
     * Validates this WorkspaceDTO instance for backup/sync.
     * Checks presence and validity of all required fields and hashes.
     * @return true if all required fields are present and valid; false otherwise
     */
    public boolean validate() {
        logger.info("Validating WorkspaceDTO...");
        boolean valid = true;

        if (projectedTransactions == null) {
            logger.error("Validation failed: projectedTransactions is null.");
            valid = false;
        } else {
            logger.info("projectedTransactions size: {}", projectedTransactions.size());
        }

        if (budgetTransactions == null) {
            logger.error("Validation failed: budgetTransactions is null.");
            valid = false;
        } else {
            logger.info("budgetTransactions size: {}", budgetTransactions.size());
        }

        if (localCacheState == null) {
            logger.error("Validation failed: localCacheState is null.");
            valid = false;
        } else {
            logger.info("Validating nested LocalCacheState...");
            if (!localCacheState.validate()) {
                logger.error("Validation failed: LocalCacheState is invalid.");
                valid = false;
            }
        }

        if (budgetTransactionsHash == null || budgetTransactionsHash.trim().isEmpty()) {
            logger.error("Validation failed: budgetTransactionsHash is null or empty.");
            valid = false;
        }
        if (projectionsHash == null || projectionsHash.trim().isEmpty()) {
            logger.error("Validation failed: projectionsHash is null or empty.");
            valid = false;
        }
        if (localCacheStateHash == null || localCacheStateHash.trim().isEmpty()) {
            logger.error("Validation failed: localCacheStateHash is null or empty.");
            valid = false;
        }
        if (version == null || version.trim().isEmpty()) {
            logger.error("Validation failed: version is null or empty.");
            valid = false;
        }
        if (lastModified == null || lastModified.trim().isEmpty()) {
            logger.error("Validation failed: lastModified is null or empty.");
            valid = false;
        }

        if (valid) {
            logger.info("WorkspaceDTO validation PASSED.");
        } else {
            logger.error("WorkspaceDTO validation FAILED.");
        }
        return valid;
    }

    @Override
    public String toString() {
        return "WorkspaceDTO{" +
                "projectedTransactions=" + (projectedTransactions == null ? 0 : projectedTransactions.size()) +
                ", budgetTransactions=" + (budgetTransactions == null ? 0 : budgetTransactions.size()) +
                ", localCacheState=" + localCacheState +
                ", budgetTransactionsHash='" + budgetTransactionsHash + '\'' +
                ", projectionsHash='" + projectionsHash + '\'' +
                ", localCacheStateHash='" + localCacheStateHash + '\'' +
                ", version='" + version + '\'' +
                ", lastModified='" + lastModified + '\'' +
                '}';
    }
}