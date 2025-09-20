package service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.WorkspaceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DigitalOceanWorkspaceService {
    private static final Logger logger = LoggerFactory.getLogger(DigitalOceanWorkspaceService.class);

    private final String spacesEndpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final AmazonS3 s3;

    private static final String CLOUD_BACKUP_PREFIX = "cloud/workspace_";
    private static final String CLOUD_BACKUP_SUFFIX = ".json";
    private static final DateTimeFormatter FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    public DigitalOceanWorkspaceService() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                logger.error("application.properties file not found in classpath.");
                throw new RuntimeException("application.properties file not found in classpath.");
            }
            props.load(input);
        } catch (IOException e) {
            logger.error("Failed to load application.properties", e);
            throw new RuntimeException("Failed to load application.properties", e);
        }

        this.spacesEndpoint = props.getProperty("do.spaces.endpoint");
        this.accessKey = props.getProperty("do.spaces.access_key");
        this.secretKey = props.getProperty("do.spaces.secret_key");
        this.bucket = props.getProperty("do.spaces.bucket");

        if (spacesEndpoint == null || accessKey == null || secretKey == null || bucket == null) {
            logger.error("Missing DigitalOcean Spaces configuration properties.");
            throw new RuntimeException("Missing DigitalOcean Spaces configuration properties.");
        }

        BasicAWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setSignerOverride("AWSS3V4SignerType");

        this.s3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AmazonS3ClientBuilder.EndpointConfiguration(spacesEndpoint, "us-east-1"))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig)
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .build();

        logger.info("DigitalOceanWorkspaceService initialized for bucket '{}', endpoint '{}'.", bucket, spacesEndpoint);
    }

    /**
     * Saves a new versioned backup to /cloud, and deletes any backups older than 30 days.
     * @param workspace WorkspaceDTO to back up.
     * @throws IOException if upload fails
     */
    public void uploadWorkspaceVersioned(WorkspaceDTO workspace) throws IOException {
        logger.info("Starting versioned workspace cloud backup...");
        if (workspace == null) {
            logger.error("uploadWorkspaceVersioned: Given WorkspaceDTO is null.");
            throw new IllegalArgumentException("WorkspaceDTO cannot be null.");
        }

        // Update section hashes and metadata
        workspace.updateAllSectionHashes();
        String nowIso = Instant.now().toString();
        workspace.setLastModified(nowIso);

        if (!workspace.validate()) {
            logger.error("WorkspaceDTO failed validation before upload. Aborting upload.");
            throw new IOException("WorkspaceDTO failed validation before upload.");
        }

        String timestamp = FILENAME_FORMAT.format(Instant.now());
        String keyName = CLOUD_BACKUP_PREFIX + timestamp + CLOUD_BACKUP_SUFFIX;

        logger.info("Uploading versioned workspace to key: {}", keyName);

        ObjectMapper mapper = new ObjectMapper();
        File tempFile = File.createTempFile("workspace_", ".json");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            mapper.writeValue(fos, workspace);
        }

        PutObjectRequest putRequest = new PutObjectRequest(bucket, keyName, tempFile);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        metadata.setContentLength(tempFile.length());
        putRequest.setMetadata(metadata);

        try {
            s3.putObject(putRequest);
            logger.info("WorkspaceDTO uploaded successfully to '{}'", keyName);
        } catch (AmazonServiceException e) {
            logger.error("Upload failed: {}", e.getMessage(), e);
            throw new IOException("Workspace backup upload failed: " + e.getMessage(), e);
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }

        // Clean up old backups (>30 days)
        cleanOldCloudBackups();
    }

    /**
     * Deletes any backup in /cloud/ older than 30 days.
     */
    public void cleanOldCloudBackups() {
        logger.info("Scanning /cloud/ for backups older than 30 days to delete...");
        try {
            ObjectListing listing = s3.listObjects(bucket, "cloud/");
            List<S3ObjectSummary> toDelete = new ArrayList<>();
            Instant cutoff = Instant.now().minusSeconds(30L * 24 * 60 * 60);

            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                String key = summary.getKey();
                if (!key.startsWith(CLOUD_BACKUP_PREFIX) || !key.endsWith(CLOUD_BACKUP_SUFFIX)) continue;

                String timestampStr = key.substring(CLOUD_BACKUP_PREFIX.length(), key.length() - CLOUD_BACKUP_SUFFIX.length());
                Instant backupInstant = null;
                try {
                    backupInstant = Instant.from(FILENAME_FORMAT.parse(timestampStr));
                } catch (Exception e) {
                    logger.warn("Could not parse timestamp for backup '{}', skipping.", key);
                    continue;
                }
                if (backupInstant.isBefore(cutoff)) {
                    toDelete.add(summary);
                }
            }

            logger.info("Found {} backups to delete (older than 30 days).", toDelete.size());
            for (S3ObjectSummary oldBackup : toDelete) {
                String key = oldBackup.getKey();
                try {
                    s3.deleteObject(bucket, key);
                    logger.info("Deleted old backup '{}'", key);
                } catch (AmazonServiceException e) {
                    logger.error("Failed to delete old backup '{}': {}", key, e.getMessage(), e);
                }
            }
            logger.info("Old backup cleanup complete. {} files deleted.", toDelete.size());
        } catch (AmazonServiceException e) {
            logger.error("Failed to list or delete old backups in /cloud/: {}", e.getMessage(), e);
        }
    }

    /**
     * Downloads and deserializes the latest workspace JSON backup from DigitalOcean Spaces.
     * @return Validated WorkspaceDTO, or null if not found or validation fails.
     */
    public WorkspaceDTO downloadLatestWorkspaceBackup() {
        logger.info("Downloading the latest WorkspaceDTO backup from /cloud/...");
        try {
            ObjectListing listing = s3.listObjects(bucket, "cloud/");
            S3ObjectSummary newest = null;
            Instant newestInstant = Instant.EPOCH;
            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                String key = summary.getKey();
                if (!key.startsWith(CLOUD_BACKUP_PREFIX) || !key.endsWith(CLOUD_BACKUP_SUFFIX)) continue;
                String timestampStr = key.substring(CLOUD_BACKUP_PREFIX.length(), key.length() - CLOUD_BACKUP_SUFFIX.length());
                try {
                    Instant backupInstant = Instant.from(FILENAME_FORMAT.parse(timestampStr));
                    if (backupInstant.isAfter(newestInstant)) {
                        newestInstant = backupInstant;
                        newest = summary;
                    }
                } catch (Exception e) {
                    logger.warn("Could not parse timestamp for backup '{}', skipping.", key);
                }
            }
            if (newest == null) {
                logger.warn("No backups found in /cloud/.");
                return null;
            }
            logger.info("Latest backup found: '{}'", newest.getKey());
            S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, newest.getKey()));
            try (InputStream in = s3Object.getObjectContent()) {
                ObjectMapper mapper = new ObjectMapper();
                WorkspaceDTO workspace = mapper.readValue(in, WorkspaceDTO.class);
                logger.info("WorkspaceDTO downloaded and deserialized from '{}'", newest.getKey());

                workspace.updateAllSectionHashes();
                if (!workspace.validate()) {
                    logger.error("WorkspaceDTO hash or field validation failed after download.");
                    return null;
                }
                logger.info("WorkspaceDTO validated after download.");
                return workspace;
            }
        } catch (AmazonServiceException | IOException e) {
            logger.error("Failed to download or deserialize backup: {}", e.getMessage(), e);
        }
        return null;
    }
}