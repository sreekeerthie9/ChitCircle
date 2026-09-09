package com.ms.chitcircle.apis;

import com.ms.chitcircle.enums.KycDocumentStatusEnum;
import com.ms.chitcircle.enums.KycStatusEnum;
import com.ms.chitcircle.models.KycDocument;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.repositories.KycDocumentRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.properties.GcpProperties;
import com.ms.chitcircle.utils.GcpUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycApi {
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES = Set.of(MediaType.APPLICATION_PDF_VALUE, MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp");

  private final UserRepository userRepository;
  private final KycDocumentRepository documentRepository;
  private final GcpUtil gcpUtil;
  private final GcpProperties gcpProperties;

  @GetMapping("/documents")
  public List<Map<String, Object>> myDocuments(Authentication authentication) {
    return documentRepository.findAllByUserUsernameOrderByCreatedAtDesc(authentication.getName()).stream().map(this::view).toList();
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> upload(
      @RequestParam String documentType,
      @RequestPart MultipartFile document,
      Authentication authentication) {
    if (document == null || document.isEmpty()) throw new IllegalArgumentException("A document is required");
    if (document.getSize() > MAX_FILE_SIZE) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "KYC document must be 10 MB or smaller");
    if (document.getContentType() == null || !ALLOWED_TYPES.contains(document.getContentType().toLowerCase(Locale.ROOT))) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "KYC document must be a PDF, JPEG, PNG, or WebP file");
    }
    User user = userRepository.findByUsername(authentication.getName()).orElseThrow(() -> new EntityNotFoundException("User not found"));
    String fileName = safeFileName(document.getOriginalFilename());
    String objectKey = "kyc/" + user.getId() + "/" + UUID.randomUUID() + "-" + fileName;
    if (!gcpUtil.uploadFileToGcsBucket(bucket(), objectKey, document)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "KYC document upload failed");
    KycDocument record = new KycDocument();
    record.setUser(user);
    record.setDocumentType(documentType.trim().toUpperCase(Locale.ROOT));
    record.setObjectKey(objectKey);
    record.setFileName(fileName);
    record.setContentType(document.getContentType().toLowerCase(Locale.ROOT));
    record.setStatus(KycDocumentStatusEnum.PENDING);
    user.setKycStatus(KycStatusEnum.PENDING);
    userRepository.save(user);
    return view(documentRepository.save(record));
  }

  @GetMapping("/users/{username}/documents")
  public List<Map<String, Object>> userDocuments(@PathVariable String username, Authentication authentication) {
    User target = accessibleUser(username, authentication);
    return documentRepository.findAllByUserUsernameOrderByCreatedAtDesc(target.getUsername()).stream().map(this::view).toList();
  }

  @GetMapping("/review/documents")
  public List<Map<String, Object>> reviewDocuments(Authentication authentication) {
    requireSuperAdmin(authentication);
    return documentRepository.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
  }

  @GetMapping("/documents/{documentId}/download")
  public ResponseEntity<InputStreamResource> download(@PathVariable Long documentId, Authentication authentication) {
    KycDocument document = documentRepository.findById(documentId).orElseThrow(() -> new EntityNotFoundException("KYC document not found"));
    accessibleUser(document.getUser().getUsername(), authentication);
    try {
      InputStream stream = gcpUtil.downloadFile(bucket(), document.getObjectKey());
      return ResponseEntity.ok().contentType(MediaType.parseMediaType(document.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFileName(document.getFileName()) + "\"")
        .body(new InputStreamResource(stream));
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC document is unavailable", exception);
    }
  }

  @PostMapping("/documents/{documentId}/review")
  @Transactional
  public Map<String, Object> review(
      @PathVariable Long documentId,
      @RequestBody Map<String, Object> body,
      Authentication authentication) {
    KycDocument document = documentRepository.findById(documentId).orElseThrow(() -> new EntityNotFoundException("KYC document not found"));
    User target = accessibleUser(document.getUser().getUsername(), authentication);
    if (isCustomer(authentication)) throw new AccessDeniedException("Only an admin can review KYC documents");
    String decision = String.valueOf(body.getOrDefault("status", "")).trim().toUpperCase(Locale.ROOT);
    KycDocumentStatusEnum status = KycDocumentStatusEnum.valueOf(decision);
    if (status != KycDocumentStatusEnum.VERIFIED && status != KycDocumentStatusEnum.REJECTED) throw new IllegalArgumentException("KYC review status must be VERIFIED or REJECTED");
    document.setStatus(status);
    document.setReviewNote(body.get("note") == null ? null : body.get("note").toString().trim());
    document.setReviewedBy(authentication.getName());
    document.setReviewedAt(OffsetDateTime.now());
    target.setKycStatus(status == KycDocumentStatusEnum.VERIFIED ? KycStatusEnum.VERIFIED : KycStatusEnum.REJECTED);
    userRepository.save(target);
    return view(documentRepository.save(document));
  }

  private User accessibleUser(String username, Authentication authentication) {
    User actor = userRepository.findByUsername(authentication.getName()).orElseThrow(() -> new EntityNotFoundException("User not found"));
    User target = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("User not found"));
    String role = actor.getRole() == null ? "" : actor.getRole().getName();
    boolean allowed = actor.getId().equals(target.getId())
      || "SUPERADMIN".equals(role)
      || ("ADMIN".equals(role) && target.getParent() != null && actor.getId().equals(target.getParent().getId()));
    if (!allowed) throw new AccessDeniedException("KYC record is outside your scope");
    return target;
  }

  private void requireSuperAdmin(Authentication authentication) {
    User actor = userRepository.findByUsername(authentication.getName()).orElseThrow(() -> new EntityNotFoundException("User not found"));
    if (actor.getRole() == null || !"SUPERADMIN".equals(actor.getRole().getName())) {
      throw new AccessDeniedException("Only super admins can view all KYC documents");
    }
  }

  private boolean isCustomer(Authentication authentication) {
    User user = userRepository.findByUsername(authentication.getName()).orElseThrow(() -> new EntityNotFoundException("User not found"));
    return user.getRole() == null || "CUSTOMER".equals(user.getRole().getName());
  }

  private String bucket() {
    String bucket = gcpProperties.getStorage().getKycUploadBucket();
    if (bucket == null || bucket.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "KYC storage is not configured");
    return bucket;
  }

  private Map<String, Object> view(KycDocument document) {
    return Map.of(
      "id", document.getId(), "username", document.getUser().getUsername(), "documentType", document.getDocumentType(),
      "fileName", document.getFileName(), "contentType", document.getContentType(), "status", document.getStatus(),
      "reviewNote", document.getReviewNote() == null ? "" : document.getReviewNote(),
      "reviewedBy", document.getReviewedBy() == null ? "" : document.getReviewedBy(),
      "createdAt", document.getCreatedAt() == null ? "" : document.getCreatedAt(),
      "downloadUrl", "/api/kyc/documents/" + document.getId() + "/download"
    );
  }

  private String safeFileName(String original) {
    String fileName = original == null ? "document" : original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", "_");
    return fileName.isBlank() ? "document" : fileName.length() > 120 ? fileName.substring(fileName.length() - 120) : fileName;
  }
}
