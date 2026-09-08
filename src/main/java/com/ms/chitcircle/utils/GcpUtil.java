package com.ms.chitcircle.utils;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.CopyRequest;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.ms.chitcircle.properties.GcpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class GcpUtil {

  private static final long PRESIGNED_PUT_URL_DURATION_MINUTES = 15;
  private static final long PRESIGNED_GET_URL_DURATION_MINUTES = 60;
  private static final String SPRING_PROFILE_PROD = "prod";

  private final GcpProperties gcpProperties;
  private final Storage storage;
  private final SecretManagerServiceClient secretManagerServiceClient;
  private final JsonMapper objectMapper;
  private final Environment environment;

  public String withObjectKeyPrefix(String key) {
    if (key == null || key.isBlank()) {
      return key;
    }

    String normalizedKey = stripLeadingSlash(key.trim());
    String prefix = gcpProperties.getStorage().getObjectKeyPrefix();
    if (prefix == null || prefix.isBlank()) {
      return normalizedKey;
    }

    String normalizedPrefix = stripLeadingSlash(prefix.trim());
    normalizedPrefix = normalizedPrefix.endsWith("/") ? normalizedPrefix : normalizedPrefix + "/";
    return normalizedKey.startsWith(normalizedPrefix) ? normalizedKey : normalizedPrefix + normalizedKey;
  }

  public String withObjectKeyPrefixInUrl(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }

    if (!value.matches("https?://.*")) {
      return withObjectKeyPrefix(value);
    }

    int protocolEnd = value.indexOf("://") + 3;
    int pathStart = value.indexOf('/', protocolEnd);
    if (pathStart < 0 || pathStart == value.length() - 1) {
      return value;
    }

    String baseUrl = value.substring(0, pathStart + 1);
    String objectKey = value.substring(pathStart + 1);
    return baseUrl + withObjectKeyPrefix(objectKey);
  }


  /**
   * Fetches the latest version of a secret from Secret Manager and deserializes it.
   * secretName can be a short name (resolved against the configured project) or a
   * fully-qualified "projects/{project}/secrets/{secret}" path.
   */
  public <T> T getSecret(String secretName, Class<T> clazz) {
    log.debug("Getting secret: {}", secretName);
    try {
      SecretVersionName versionName = SecretVersionName.of(
        gcpProperties.getProjectId(), secretName, "latest");
      String payload = secretManagerServiceClient
        .accessSecretVersion(versionName)
        .getPayload()
        .getData()
        .toStringUtf8();
      return objectMapper.readValue(payload, clazz);
    } catch (Exception e) {
      throw new RuntimeException("Error retrieving secret from GCP Secret Manager", e);
    }
  }

  public boolean isProd() {
    return Arrays.asList(environment.getActiveProfiles()).contains(SPRING_PROFILE_PROD);
  }

  public String getPresignedPutUrlForImageUpload(String fileName, String bucketName) {
    BlobInfo blobInfo = buildBlobInfo(bucketName, fileName);
    URL url = storage.signUrl(
      blobInfo,
      PRESIGNED_PUT_URL_DURATION_MINUTES, TimeUnit.MINUTES,
      SignUrlOption.httpMethod(com.google.cloud.storage.HttpMethod.PUT),
      SignUrlOption.withV4Signature());
    return url.toString();
  }

  public String getPresignedGetUrlForImageUpload(String fileName, String bucketName) {
    BlobInfo blobInfo = buildBlobInfo(bucketName, fileName);
    URL url = storage.signUrl(
      blobInfo,
      PRESIGNED_GET_URL_DURATION_MINUTES, TimeUnit.MINUTES,
      SignUrlOption.httpMethod(com.google.cloud.storage.HttpMethod.GET),
      SignUrlOption.withV4Signature());
    return url.toString();
  }

  public void deleteGcsObject(String bucketName, String key) {
    String objectKey = withObjectKeyPrefix(key);
    try {
      boolean deleted = storage.delete(BlobId.of(bucketName, objectKey));
      if (deleted) {
        log.debug("Deleted GCS object: bucket={}, key={}", bucketName, objectKey);
      } else {
        log.warn("GCS object not found for delete: bucket={}, key={}", bucketName, objectKey);
      }
    } catch (Exception e) {
      log.warn("Failed to delete GCS object: bucket={}, key={}", bucketName, objectKey, e);
    }
  }

  public InputStream downloadFile(String bucketName, String key) {
    String objectKey = withObjectKeyPrefix(key);
    Blob blob = storage.get(BlobId.of(bucketName, objectKey));
    if (blob == null && !objectKey.equals(key)) {
      // fall back to the unprefixed key, mirroring the old S3 fallback behavior
      blob = storage.get(BlobId.of(bucketName, key));
    }
    if (blob == null) {
      throw new RuntimeException("Object not found: bucket=" + bucketName + ", key=" + key);
    }
    return new ByteArrayInputStream(blob.getContent());
  }

  public void deleteImageFromGcs(String filename) {
    if (isProd()) {
      try {
        String bucket = gcpProperties.getStorage().getImageUploadBucket();
        storage.delete(BlobId.of(bucket, filename));
        String writeKey = withObjectKeyPrefix(filename);
        if (!writeKey.equals(filename)) {
          storage.delete(BlobId.of(bucket, writeKey));
        }
      } catch (Exception e) {
        log.error("Error deleting the image from GCS {}", filename);
      }
    } else {
      log.error("Cannot delete image: {} from GCS since the active profile is local", filename);
    }
  }

  public boolean uploadFileToGcsBucket(String bucketName, String fileName, MultipartFile file) {
    try {
      BlobId blobId = BlobId.of(bucketName, withObjectKeyPrefix(fileName));
      BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
        .setContentType(file.getContentType())
        .build();
      storage.create(blobInfo, file.getBytes());
      return true;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void uploadFile(String bucketName, File filePath, String key) throws IOException {
    BlobId blobId = BlobId.of(bucketName, withObjectKeyPrefix(key));
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storage.create(blobInfo, Files.readAllBytes(filePath.toPath()));
    log.debug("Uploaded to GCS: {}", blobId.getName());
  }

  /**
   * Stub — see class-level javadoc. Only implement this if Cloud CDN is actually
   * sitting in front of the bucket via a Load Balancer; otherwise there is no
   * cache layer to invalidate and this method should not be called.
   */
  public void invalidateCdnCache(String path) {
    log.warn("invalidateCdnCache called for path={} — no CDN configured; skipping. "
      + "Wire this to the Compute Engine urlMaps.invalidateCache API if Cloud CDN is added.", path);
  }

  public List<File> fetchFilesFromGcs(String bucketName, List<String> keys) throws IOException {
    List<File> files = new ArrayList<>();

    for (String key : keys) {
      byte[] bytes = getObjectAsBytes(bucketName, key);
      String fileName = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
      files.add(createValidTempFile(bytes, fileName));
    }

    return files;
  }

  public File createValidTempFile(byte[] bytes, String originalFileName) throws IOException {
    String baseName = originalFileName.contains(".")
      ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
      : originalFileName;
    String extension = originalFileName.contains(".")
      ? originalFileName.substring(originalFileName.lastIndexOf("."))
      : "";

    baseName = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");

    Path tempFile = Files.createTempFile(baseName, extension);
    tempFile.toFile().deleteOnExit();

    Files.write(tempFile, bytes);
    return tempFile.toFile();
  }

  private byte[] getObjectAsBytes(String bucketName, String key) {
    String objectKey = withObjectKeyPrefix(key);
    Blob blob = storage.get(BlobId.of(bucketName, objectKey));
    if (blob == null && !objectKey.equals(key)) {
      blob = storage.get(BlobId.of(bucketName, key));
    }
    if (blob == null) {
      throw new RuntimeException("Object not found: bucket=" + bucketName + ", key=" + key);
    }
    return blob.getContent();
  }

  private BlobInfo buildBlobInfo(String bucketName, String key) {
    return BlobInfo.newBuilder(BlobId.of(bucketName, withObjectKeyPrefix(key))).build();
  }

  private void copyObject(String fromBucketName, String toBucketName, String fromKey, String toKey) {
    BlobId sourceId = BlobId.of(fromBucketName, fromKey);
    BlobInfo targetInfo = BlobInfo.newBuilder(BlobId.of(toBucketName, toKey)).build();
    CopyRequest copyRequest = CopyRequest.newBuilder()
      .setSource(sourceId)
      .setTarget(targetInfo)
      .build();
    CopyWriter copyWriter = storage.copy(copyRequest);
    copyWriter.getResult();
  }

  private String stripLeadingSlash(String value) {
    while (value.startsWith("/")) {
      value = value.substring(1);
    }
    return value;
  }
}
