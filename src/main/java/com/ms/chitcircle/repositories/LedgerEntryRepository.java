package com.ms.chitcircle.repositories;

import com.ms.chitcircle.models.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
  void deleteAllByGroupId(Long groupId);
  List<LedgerEntry> findAllByGroupIdOrderByCreatedAtDesc(Long groupId);
}
