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
import java.util.Map;
import java.util.Set;

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
    validateMemberIds(scheme, memberIds);
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
    return toDto(savedGroup);
  }

  @Transactional(readOnly = true)
  public List<GroupDto> list(String username, Long schemeId) {
    ChitScheme scheme = schemeRepository.findById(schemeId)
      .orElseThrow(() -> new EntityNotFoundException("Scheme not found: " + schemeId));
    if (!owns(username, scheme.getAdmin())) {
      throw new AccessDeniedException("Only the scheme admin can view its groups");
    }
    return toDtos(groupRepository.findAllBySchemeIdOrderByCreatedAtDesc(schemeId));
  }

  @Transactional(readOnly = true)
  public List<GroupDto> listAll(String username) {
    com.ms.chitcircle.models.User admin = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    return toDtos(groupRepository.findAllByScheme_Admin_IdAndScheme_Admin_TenantIdOrderByCreatedAtDesc(
      admin.getId(), admin.getTenantId()));
  }

  @Transactional(readOnly = true)
  public GroupDto get(String username, Long id) {
    ChitGroup group = groupRepository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    if (!owns(username, group.getScheme().getAdmin())) {
      throw new AccessDeniedException("Only the group admin can view this group");
    }
    return toDto(group);
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
    if (request.getMemberIds() != null) syncMembers(username, group, request.getMemberIds());
    return toDto(groupRepository.save(group));
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

  private GroupDto toDto(ChitGroup group) {
    return toDtos(List.of(group)).getFirst();
  }

  private List<GroupDto> toDtos(List<ChitGroup> groups) {
    if (groups.isEmpty()) {
      return List.of();
    }

    Map<Long, List<Integer>> memberIds = membershipRepository.findAllByGroupIdInAndActiveTrue(
        groups.stream().map(ChitGroup::getId).toList())
      .stream()
      .collect(java.util.stream.Collectors.groupingBy(
        membership -> membership.getGroup().getId(),
        java.util.stream.Collectors.mapping(
          membership -> membership.getUser().getId(), java.util.stream.Collectors.toList())));
    return groups.stream()
      .map(group -> GroupDto.fromEntity(group, memberIds.getOrDefault(group.getId(), List.of())))
      .toList();
  }

  private void validateMemberIds(ChitScheme scheme, List<Integer> memberIds) {
    if (new HashSet<>(memberIds).size() != memberIds.size()) {
      throw new IllegalArgumentException("Duplicate members are not allowed");
    }
    if (memberIds.size() > scheme.getMemberCount()) {
      throw new IllegalArgumentException("Selected members exceed the scheme member limit");
    }
  }

  private void syncMembers(String username, ChitGroup group, List<Integer> memberIds) {
    validateMemberIds(group.getScheme(), memberIds);
    Map<Integer, Membership> existingMembers = membershipRepository
      .findAllByGroupIdOrderByJoinedAtDesc(group.getId())
      .stream()
      .collect(java.util.stream.Collectors.toMap(membership -> membership.getUser().getId(), membership -> membership));
    Set<Integer> selectedMemberIds = new HashSet<>(memberIds);

    for (Integer memberId : selectedMemberIds) {
      com.ms.chitcircle.models.User member = userRepository.findById(memberId)
        .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + memberId));
      if (!isCustomerInScope(username, member)) {
        throw new AccessDeniedException("Customer belongs to another tenant or parent scope");
      }
      Membership existingMembership = existingMembers.get(memberId);
      if (existingMembership == null) {
        Membership membership = new Membership();
        membership.setGroup(group);
        membership.setUser(member);
        membershipRepository.save(membership);
      } else if (!existingMembership.isActive()) {
        existingMembership.setActive(true);
      }
    }

    existingMembers.forEach((memberId, membership) -> {
      if (!selectedMemberIds.contains(memberId)) {
        membership.setActive(false);
      }
    });
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
