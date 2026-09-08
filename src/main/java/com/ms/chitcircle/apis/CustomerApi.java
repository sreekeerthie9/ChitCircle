package com.ms.chitcircle.apis;

import com.ms.chitcircle.enums.CycleStatusEnum;
import com.ms.chitcircle.models.Bid;
import com.ms.chitcircle.models.Claim;
import com.ms.chitcircle.models.Cycle;
import com.ms.chitcircle.models.Membership;
import com.ms.chitcircle.models.User;
import com.ms.chitcircle.repositories.BidRepository;
import com.ms.chitcircle.repositories.ChitGroupRepository;
import com.ms.chitcircle.repositories.ClaimRepository;
import com.ms.chitcircle.repositories.CycleRepository;
import com.ms.chitcircle.repositories.MembershipRepository;
import com.ms.chitcircle.repositories.PaymentRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.services.AuditService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerApi {
  private final UserRepository userRepository;
  private final MembershipRepository membershipRepository;
  private final BidRepository bidRepository;
  private final ClaimRepository claimRepository;
  private final CycleRepository cycleRepository;
  private final PaymentRepository paymentRepository;
  private final AuditService auditService;

  @GetMapping("/memberships")
  public List<Map<String, Object>> memberships(org.springframework.security.core.Authentication authentication) {
    return membershipRepository.findAllByUserUsernameOrderByJoinedAtDesc(authentication.getName())
      .stream().map(this::membershipView).toList();
  }

  @GetMapping("/bids")
  public List<Map<String, Object>> bids(org.springframework.security.core.Authentication authentication) {
    return bidRepository.findAllByMembershipUserUsernameOrderBySubmittedAtDesc(authentication.getName())
      .stream().map(this::bidView).toList();
  }

  @GetMapping("/claims")
  public List<Map<String, Object>> claims(org.springframework.security.core.Authentication authentication) {
    return claimRepository.findAllByMembershipUserUsernameOrderBySubmittedAtDesc(authentication.getName())
      .stream().map(this::claimView).toList();
  }

  @GetMapping("/payments")
  public List<Map<String, Object>> payments(org.springframework.security.core.Authentication authentication) {
    return paymentRepository.findAllByMembershipUserUsernameOrderByDueDateDesc(authentication.getName())
      .stream().map(this::paymentView).toList();
  }

  @GetMapping("/cycles")
  public List<Map<String, Object>> cycles(org.springframework.security.core.Authentication authentication) {
    User user = userRepository.findByUsername(authentication.getName())
      .orElseThrow(() -> new EntityNotFoundException("User not found"));
    return membershipRepository.findAllByUserUsernameOrderByJoinedAtDesc(authentication.getName()).stream()
      .filter(Membership::isActive)
      .flatMap(membership -> cycleRepository.findAllByGroupIdOrderByCycleNumberDesc(membership.getGroup().getId()).stream()
        .map(cycle -> cycleView(cycle, membership)))
      .toList();
  }

  @PostMapping("/cycles/{cycleId}/bids")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submitBid(
      @PathVariable Long cycleId,
      @RequestBody Map<String, Object> body,
      org.springframework.security.core.Authentication authentication) {
    throw new ResponseStatusException(HttpStatus.GONE, "Discount bidding is not available. Submit a claim for this month's chit instead.");
  }

  @PostMapping("/cycles/{cycleId}/claims")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submitClaim(
      @PathVariable Long cycleId,
      @RequestBody Map<String, Object> body,
      org.springframework.security.core.Authentication authentication) {
    Membership membership = membershipFor(authentication.getName(), cycleId);
    Cycle cycle = cycleRepository.findById(cycleId)
      .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + cycleId));
    ensureClaimWindowOpen(cycle);
    if (claimRepository.findByCycleIdAndMembershipId(cycleId, membership.getId()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already submitted a claim for this month");
    }
    Claim claim = new Claim();
    claim.setCycle(cycle);
    claim.setMembership(membership);
    claim.setNote(body.get("note") == null ? null : body.get("note").toString());
    Claim saved = claimRepository.save(claim);
    auditService.record(authentication.getName(), "CLAIM_SUBMITTED", "CLAIM", saved.getId(), "{\"status\":\"PENDING\"}");
    return claimView(saved);
  }

  private Membership membershipFor(String username, Long cycleId) {
    User user = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("User not found"));
    Cycle cycle = cycleRepository.findById(cycleId)
      .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + cycleId));
    return membershipRepository.findByUserIdAndGroupId(user.getId(), cycle.getGroup().getId())
      .filter(Membership::isActive)
      .orElseThrow(() -> new SecurityException("Customer is not enrolled in this group"));
  }

  private void ensureClaimWindowOpen(Cycle cycle) {
    if (cycle.getStatus() != CycleStatusEnum.BIDDING_OPEN) {
      throw new IllegalStateException("Claims are not open for this cycle");
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (cycle.getBidWindowOpenAt() != null && now.isBefore(cycle.getBidWindowOpenAt())) {
      throw new IllegalStateException("The claim window has not opened");
    }
    if (cycle.getBidWindowCloseAt() != null && now.isAfter(cycle.getBidWindowCloseAt())) {
      throw new IllegalStateException("The claim window has closed");
    }
  }

  private String required(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(key + " is required");
    return value.toString();
  }

  private Map<String, Object> membershipView(Membership membership) {
    return Map.of(
      "id", membership.getId(),
      "groupId", membership.getGroup().getId(),
      "groupName", membership.getGroup().getName(),
      "schemeId", membership.getGroup().getScheme().getId(),
      "active", membership.isActive(),
      "hasWon", membership.isHasWon(),
      "joinedAt", membership.getJoinedAt() == null ? "" : membership.getJoinedAt()
    );
  }

  private Map<String, Object> cycleView(Cycle cycle, Membership membership) {
    return Map.ofEntries(
      Map.entry("id", cycle.getId()),
      Map.entry("cycleNumber", cycle.getCycleNumber()),
      Map.entry("groupId", cycle.getGroup().getId()),
      Map.entry("groupName", cycle.getGroup().getName()),
      Map.entry("schemeName", cycle.getGroup().getScheme().getName()),
      Map.entry("potAmount", cycle.getGroup().getScheme().getPotAmount()),
      Map.entry("status", cycle.getStatus()),
      Map.entry("membershipId", membership.getId()),
      Map.entry("hasClaim", claimRepository.findByCycleIdAndMembershipId(cycle.getId(), membership.getId()).isPresent()),
      Map.entry("bidWindowOpenAt", cycle.getBidWindowOpenAt() == null ? "" : cycle.getBidWindowOpenAt()),
      Map.entry("bidWindowCloseAt", cycle.getBidWindowCloseAt() == null ? "" : cycle.getBidWindowCloseAt())
    );
  }

  private Map<String, Object> bidView(Bid bid) {
    return Map.of(
      "id", bid.getId(),
      "cycleId", bid.getCycle().getId(),
      "cycleNumber", bid.getCycle().getCycleNumber(),
      "groupId", bid.getCycle().getGroup().getId(),
      "discountAmount", bid.getDiscountAmount(),
      "status", bid.getStatus(),
      "submittedAt", bid.getSubmittedAt() == null ? "" : bid.getSubmittedAt()
    );
  }

  private Map<String, Object> claimView(Claim claim) {
    return Map.of(
      "id", claim.getId(),
      "cycleId", claim.getCycle().getId(),
      "cycleNumber", claim.getCycle().getCycleNumber(),
      "groupId", claim.getCycle().getGroup().getId(),
      "note", claim.getNote() == null ? "" : claim.getNote(),
      "status", claim.getStatus(),
      "submittedAt", claim.getSubmittedAt() == null ? "" : claim.getSubmittedAt()
    );
  }

  private Map<String, Object> paymentView(com.ms.chitcircle.models.Payment payment) {
    return Map.of(
      "id", payment.getId(),
      "cycleId", payment.getCycle().getId(),
      "groupId", payment.getCycle().getGroup().getId(),
      "amount", payment.getAmount(),
      "status", payment.getStatus(),
      "method", payment.getMethod() == null ? "" : payment.getMethod(),
      "dueDate", payment.getDueDate(),
      "paidAt", payment.getPaidAt() == null ? "" : payment.getPaidAt()
    );
  }
}
