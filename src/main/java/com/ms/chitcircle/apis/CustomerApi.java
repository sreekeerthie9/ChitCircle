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
import com.ms.chitcircle.repositories.PayoutRepository;
import com.ms.chitcircle.repositories.UserRepository;
import com.ms.chitcircle.security.UserImpl;
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
  private final PayoutRepository payoutRepository;
  private final AuditService auditService;

  @GetMapping("/memberships")
  public List<Map<String, Object>> memberships(org.springframework.security.core.Authentication authentication) {
    return membershipRepository.findAllByUserIdOrderByJoinedAtDesc(currentUserId(authentication))
      .stream().map(this::membershipView).toList();
  }

  @GetMapping("/bids")
  public List<Map<String, Object>> bids(org.springframework.security.core.Authentication authentication) {
    return bidRepository.findAllByMembershipUserIdOrderBySubmittedAtDesc(currentUserId(authentication))
      .stream().map(this::bidView).toList();
  }

  @GetMapping("/claims")
  public List<Map<String, Object>> claims(org.springframework.security.core.Authentication authentication) {
    return claimRepository.findAllByMembershipUserIdOrderBySubmittedAtDesc(currentUserId(authentication))
      .stream().map(this::claimView).toList();
  }

  @GetMapping("/payments")
  public List<Map<String, Object>> payments(org.springframework.security.core.Authentication authentication) {
    List<Map<String, Object>> records = new java.util.ArrayList<>();
    paymentRepository.findAllByMembershipUserUsernameOrderByDueDateDesc(authentication.getName())
      .forEach(payment -> records.add(paymentView(payment)));
    payoutRepository.findAllByMembershipUserUsernameOrderByPaidAtDesc(authentication.getName())
      .forEach(payout -> records.add(payoutView(payout)));
    records.sort((left, right) -> String.valueOf(right.get("sortDate")).compareTo(String.valueOf(left.get("sortDate"))));
    records.forEach(record -> record.remove("sortDate"));
    return records;
  }

  @GetMapping("/cycles")
  public List<Map<String, Object>> cycles(org.springframework.security.core.Authentication authentication) {
    return membershipRepository.findAllByUserIdOrderByJoinedAtDesc(currentUserId(authentication)).stream()
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

  private Integer currentUserId(org.springframework.security.core.Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserImpl user) return user.getId();
    return userRepository.findByUsername(authentication.getName())
      .orElseThrow(() -> new EntityNotFoundException("User not found")).getId();
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
      "groupName", bid.getCycle().getGroup().getName(),
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
      "groupName", claim.getCycle().getGroup().getName(),
      "note", claim.getNote() == null ? "" : claim.getNote(),
      "status", claim.getStatus(),
      "submittedAt", claim.getSubmittedAt() == null ? "" : claim.getSubmittedAt()
    );
  }

  private Map<String, Object> paymentView(com.ms.chitcircle.models.Payment payment) {
    Map<String, Object> view = new java.util.HashMap<>(Map.of(
      "id", payment.getId(),
      "recordType", "CONTRIBUTION",
      "cycleId", payment.getCycle().getId(),
      "groupId", payment.getCycle().getGroup().getId(),
      "amount", payment.getAmount(),
      "status", payment.getStatus(),
      "method", payment.getMethod() == null ? "" : payment.getMethod(),
      "dueDate", payment.getDueDate(),
      "paidAt", payment.getPaidAt() == null ? "" : payment.getPaidAt(),
      "sortDate", payment.getPaidAt() == null ? payment.getDueDate() : payment.getPaidAt()
    ));
    return view;
  }

  private Map<String, Object> payoutView(com.ms.chitcircle.models.Payout payout) {
    Map<String, Object> view = new java.util.HashMap<>();
    view.put("id", payout.getId());
    view.put("recordType", "PAYOUT");
    view.put("cycleId", payout.getCycle().getId());
    view.put("cycleNumber", payout.getCycle().getCycleNumber());
    view.put("groupId", payout.getCycle().getGroup().getId());
    view.put("amount", payout.getAmount());
    view.put("status", payout.getStatus());
    view.put("method", payout.getMethod());
    view.put("dueDate", "");
    view.put("paidAt", payout.getPaidAt());
    view.put("sortDate", payout.getPaidAt());
    return view;
  }
}
