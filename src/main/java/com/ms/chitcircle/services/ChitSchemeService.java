package com.ms.chitcircle.services;

import com.ms.chitcircle.dtos.CreateSchemeRequest;
import com.ms.chitcircle.dtos.SchemeDto;
import com.ms.chitcircle.dtos.UpdateSchemeRequest;
import com.ms.chitcircle.dtos.BulkSchemeScheduleRequest;
import com.ms.chitcircle.dtos.SchemeScheduleRow;
import com.ms.chitcircle.dtos.UpdateSchemeScheduleRequest;
import com.ms.chitcircle.models.ChitScheme;
import com.ms.chitcircle.models.ChitSchemeSchedule;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.exceptions.InvalidSchemeScheduleException;
import com.ms.chitcircle.repositories.ChitSchemeRepository;
import com.ms.chitcircle.repositories.ChitGroupRepository;
import com.ms.chitcircle.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ChitSchemeService {
  private final ChitSchemeRepository schemeRepository;
  private final ChitGroupRepository groupRepository;
  private final UserRepository userRepository;

  @Transactional
  public SchemeDto create(String username, CreateSchemeRequest request) {
    User admin = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    ChitScheme scheme = new ChitScheme();
    scheme.setAdmin(admin);
    scheme.setName(request.getName().trim());
    scheme.setPotAmount(request.getPotAmount());
    scheme.setDurationMonths(request.getDurationMonths());
    scheme.setMemberCount(request.getMemberCount());
    scheme.setCommissionRate(request.getCommissionRate() == null ? BigDecimal.ZERO : request.getCommissionRate());
    return SchemeDto.fromEntity(schemeRepository.save(scheme));
  }

  @Transactional(readOnly = true)
  public List<SchemeDto> list(String username) {
    User admin = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    return schemeRepository.findAllByAdmin_IdAndAdmin_TenantIdOrderByCreatedAtDesc(admin.getId(), admin.getTenantId())
      .stream().map(SchemeDto::fromEntity).toList();
  }

  @Transactional(readOnly = true)
  public SchemeDto get(String username, Long id) {
    ChitScheme scheme = schemeRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + id));
    if (!owns(username, scheme.getAdmin())) {
      throw new org.springframework.security.access.AccessDeniedException("Only the scheme admin can view this scheme");
    }
    return SchemeDto.fromEntity(scheme);
  }

  @Transactional
  public SchemeDto update(String username, Long id, UpdateSchemeRequest request) {
    ChitScheme scheme = schemeRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + id));
    if (!owns(username, scheme.getAdmin())) {
      throw new org.springframework.security.access.AccessDeniedException("Only the scheme admin can update a scheme");
    }
    scheme.setName(request.getName().trim());
    if (request.getPotAmount() != null) scheme.setPotAmount(request.getPotAmount());
    if (request.getDurationMonths() != null) scheme.setDurationMonths(request.getDurationMonths());
    if (request.getMemberCount() != null) scheme.setMemberCount(request.getMemberCount());
    if (request.getCommissionRate() != null) scheme.setCommissionRate(request.getCommissionRate());
    return SchemeDto.fromEntity(schemeRepository.save(scheme));
  }

  @Transactional
  public void delete(String username, Long id) {
    ChitScheme scheme = schemeRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + id));
    if (!owns(username, scheme.getAdmin())) {
      throw new org.springframework.security.access.AccessDeniedException("Only the scheme admin can delete a scheme");
    }
    if (groupRepository.existsBySchemeId(id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete this scheme because one or more chit groups are still linked to it. Delete the groups first.");
    }
    schemeRepository.delete(scheme);
  }

  @Transactional
  public SchemeDto updateSchedule(String username, Long id, UpdateSchemeScheduleRequest request) {
    ChitScheme scheme = ownedScheme(username, id);
    replaceSchedule(scheme, request.getSchedule());
    return SchemeDto.fromEntity(schemeRepository.save(scheme));
  }

  @Transactional
  public SchemeDto bulkUpdateSchedule(String username, BulkSchemeScheduleRequest request) {
    ChitScheme scheme = ownedScheme(username, request.getSchemeId());
    replaceSchedule(scheme, request.getSchedule());
    return SchemeDto.fromEntity(schemeRepository.save(scheme));
  }

  private void replaceSchedule(ChitScheme scheme, List<SchemeScheduleRow> rows) {
    Set<Integer> monthNumbers = new HashSet<>();
    if (rows.size() > scheme.getDurationMonths()) {
      throw new InvalidSchemeScheduleException("Schedule cannot exceed the scheme duration");
    }
    scheme.getSchedule().clear();
    for (SchemeScheduleRow row : rows) {
      if (!monthNumbers.add(row.getMonthNumber())) {
        throw new InvalidSchemeScheduleException("Duplicate month number: " + row.getMonthNumber());
      }
      ChitSchemeSchedule schedule = new ChitSchemeSchedule();
      schedule.setScheme(scheme);
      schedule.setMonthNumber(row.getMonthNumber());
      schedule.setMonth(row.getMonth() == null || row.getMonth().isBlank() ? "Month " + row.getMonthNumber() : row.getMonth().trim());
      schedule.setYear(row.getYear() == null ? 0 : row.getYear());
      schedule.setPitAmount(row.getPitAmount());
      schedule.setMemberPayment(row.getMemberPayment());
      scheme.getSchedule().add(schedule);
    }
  }

  private ChitScheme ownedScheme(String username, Long id) {
    ChitScheme scheme = schemeRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + id));
    if (!owns(username, scheme.getAdmin())) {
      throw new org.springframework.security.access.AccessDeniedException("Only the scheme admin can update its schedule");
    }
    return scheme;
  }

  private boolean owns(String username, User owner) {
    User actor = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    return actor.getId().equals(owner.getId())
      && actor.getTenantId() != null
      && actor.getTenantId().equals(owner.getTenantId());
  }
}
