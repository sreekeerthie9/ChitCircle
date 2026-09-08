package com.ms.chitcircle.models;

import com.ms.chitcircle.enums.KycDocumentStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
public class KycDocument {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "document_type", nullable = false, length = 50)
  private String documentType;

  @Column(name = "object_key", nullable = false, length = 1024)
  private String objectKey;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private KycDocumentStatusEnum status = KycDocumentStatusEnum.PENDING;

  @Column(name = "review_note", columnDefinition = "TEXT")
  private String reviewNote;

  @Column(name = "reviewed_by", length = 255)
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
