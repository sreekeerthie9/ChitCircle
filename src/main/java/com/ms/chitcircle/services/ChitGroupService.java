package com.ms.chitcircle.services;

import com.ms.chitcircle.dtos.CreateGroupRequest;
import com.ms.chitcircle.dtos.GroupDto;
import com.ms.chitcircle.dtos.UpdateGroupRequest;
import com.ms.chitcircle.models.ChitGroup;
import com.ms.chitcircle.models.ChitScheme;
import com.ms.chitcircle.models.Membership;
import com.ms.chitcircle.repositories.ChitGroupRepository;
import com.ms.chitcircle.repositories.ChitSchemeRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.repositories.MembershipRepository;
import com.ms.chitcircle.repositories.CycleRepository;
import com.ms.chitcircle.repositories.BidRepository;
import com.ms.chitcircle.repositories.ClaimRepository;
import com.ms.chitcircle.repositories.PaymentRepository;
import com.ms.chitcircle.repositories.LedgerEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ChitGroupService {
  private final ChitGroupRepository groupRepository;
  private final ChitSchemeRepository schemeRepository;
  private final UserRepository userRepository;
  private final MembershipRepository membershipRepository;
  private final CycleRepository cycleRepository;
  private final BidRepository bidRepository;
  private final ClaimRepository claimRepository;
  private final PaymentRepository paymentRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  @Transactional
  public GroupDto create(String username, CreateGroupRequest request) {
    ChitScheme scheme = schemeRepository.findById(request.getSchemeId())
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + request.getSchemeId()));
    if (!owns(username, scheme.getAdmin())) {
      throw new AccessDeniedException("Only the scheme admin can create a group");
    }
    ChitGroup group = new ChitGroup();
    group.setScheme(scheme);
    group.setName(request.getName().trim());
    group.setStartDate(request.getStartDate());
    List<Integer> memberIds = request.getMemberIds() == null ? List.of() : request.getMemberIds();
    if (new HashSet<>(memberIds).size() != memberIds.size()) {
      throw new IllegalArgumentException("Duplicate members are not allowed");
    }
    if (memberIds.size() > scheme.getMemberCount()) {
      throw new IllegalArgumentException("Selected members exceed the scheme member limit");
    }
    ChitGroup savedGroup = groupRepository.save(group);
    for (Integer memberId : memberIds) {
      com.ms.chitcircle.models.User member = userRepository.findById(memberId)
        .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + memberId));
      if (!isCustomerInScope(username, member)) {
        throw new AccessDeniedException("Customer belongs to another tenant or parent scope");
      }
      Membership membership = new Membership();
      membership.setGroup(savedGroup);
      membership.setUser(member);
      membershipRepository.save(membership);
    }
    return GroupDto.fromEntity(savedGroup);
  }

  @Transactional(readOnly = true)
  public List<GroupDto> list(String username, Long schemeId) {
    ChitScheme scheme = schemeRepository.findById(schemeId)
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + schemeId));
    if (!owns(username, scheme.getAdmin())) {
      throw new AccessDeniedException("Only the scheme admin can view its groups");
    }
    return groupRepository.findAllBySchemeIdOrderByCreatedAtDesc(schemeId)
      .stream().map(GroupDto::fromEntity).toList();
  }

  @Transactional(readOnly = true)
  public List<GroupDto> listAll(String username) {
    com.ms.chitcircle.models.User admin = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    return groupRepository.findAllByScheme_Admin_IdAndScheme_Admin_TenantIdOrderByCreatedAtDesc(
        admin.getId(), admin.getTenantId())
      .stream().map(GroupDto::fromEntity).toList();
  }

  @Transactional(readOnly = true)
  public GroupDto get(String username, Long id) {
    ChitGroup group = groupRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    if (!owns(username, group.getScheme().getAdmin())) {
      throw new AccessDeniedException("Only the group admin can view this group");
    }
    return GroupDto.fromEntity(group);
  }

  @Transactional
  public GroupDto update(String username, Long id, UpdateGroupRequest request) {
    ChitGroup group = groupRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    if (!owns(username, group.getScheme().getAdmin())) {
      throw new AccessDeniedException("Only the scheme admin can update a group");
    }
    if (request.getName() != null && !request.getName().isBlank()) group.setName(request.getName().trim());
    if (request.getStartDate() != null) group.setStartDate(request.getStartDate());
    if (request.getStatus() != null) group.setStatus(request.getStatus());
    return GroupDto.fromEntity(groupRepository.save(group));
  }

  @Transactional
  public void delete(String username, Long id) {
    ChitGroup group = groupRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    if (!owns(username, group.getScheme().getAdmin())) {
      throw new AccessDeniedException("Only the scheme admin can delete a group");
    }
    var cycles = cycleRepository.findAllByGroupIdOrderByCycleNumberDesc(id);
    cycles.forEach(cycle -> cycle.setWinnerMembership(null));
    cycleRepository.saveAll(cycles);
    cycleRepository.flush();
    ledgerEntryRepository.deleteAllByGroupId(id);
    for (var cycle : cycles) {
      bidRepository.deleteAllByCycleId(cycle.getId());
      claimRepository.deleteAllByCycleId(cycle.getId());
      paymentRepository.deleteAllByCycleId(cycle.getId());
    }
    cycleRepository.deleteAll(cycles);
    cycleRepository.flush();
    membershipRepository.deleteAllByGroupId(id);
    membershipRepository.flush();
    groupRepository.delete(group);
  }

  private boolean owns(String username, com.ms.chitcircle.models.User owner) {
    com.ms.chitcircle.models.User actor = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    return actor.getId().equals(owner.getId())
      && actor.getTenantId() != null
      && actor.getTenantId().equals(owner.getTenantId());
  }

  private boolean isCustomerInScope(String username, com.ms.chitcircle.models.User customer) {
    com.ms.chitcircle.models.User actor = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    return "CUSTOMER".equals(customer.getRole() == null ? "" : customer.getRole().getName())
      && actor.getTenantId() != null
      && actor.getTenantId().equals(customer.getTenantId())
      && customer.getParent() != null
      && actor.getId().equals(customer.getParent().getId());
  }
}
