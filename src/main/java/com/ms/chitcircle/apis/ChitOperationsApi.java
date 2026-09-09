package com.ms.chitcircle.apis;

import com.ms.chitcircle.enums.*;
import com.ms.chitcircle.models.*;
import com.ms.chitcircle.repositories.*;
import com.ms.chitcircle.services.AuditService;
import com.ms.chitcircle.services.VertexAiAffordabilityService;
import com.ms.chitcircle.services.WinnerNotificationService;
import com.ms.chitcircle.dtos.AiAffordabilityRequest;
import com.ms.chitcircle.properties.GcpProperties;
import com.ms.chitcircle.utils.GcpUtil;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class ChitOperationsApi {
  private final ChitGroupRepository groupRepository;
  private final UserRepository userRepository;
  private final CycleRepository cycleRepository;
  private final MembershipRepository membershipRepository;
  private final BidRepository bidRepository;
  private final ClaimRepository claimRepository;
  private final PaymentRepository paymentRepository;
  private final PayoutRepository payoutRepository;
  private final LedgerEntryRepository ledgerRepository;
  private final AuditService auditService;
  private final WinnerNotificationService winnerNotificationService;
  private final GcpUtil gcpUtil;
  private final GcpProperties gcpProperties;
  private final VertexAiAffordabilityService vertexAiAffordabilityService;

  private static final long MAX_RECEIPT_SIZE_BYTES = 10 * 1024 * 1024;
  private static final Set<String> ALLOWED_RECEIPT_TYPES = Set.of(
    MediaType.APPLICATION_PDF_VALUE,
    MediaType.IMAGE_JPEG_VALUE,
    MediaType.IMAGE_PNG_VALUE,
    "image/webp"
  );

  @GetMapping("/api/cycles")
  public List<Map<String, Object>> allCycles(Authentication authentication) {
    return cycleRepository.findAll().stream()
      .filter(cycle -> owns(cycle.getGroup(), authentication))
      .map(this::cycleView).toList();
  }

  @GetMapping("/api/payments")
  public List<Map<String, Object>> allPayments(Authentication authentication) {
    return paymentRepository.findAll().stream()
      .filter(payment -> owns(payment.getCycle().getGroup(), authentication))
      .map(this::paymentView).toList();
  }

  @PostMapping("/api/payments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> recordPayment(@RequestBody Map<String, Object> body, Authentication authentication) {
    Cycle cycle = ownedCycle(number(body, "cycleId", 0).longValue(), authentication);
    User user = userRepository.findByUsername(string(body, "username"))
      .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    Membership membership = membershipRepository.findByUserIdAndGroupId(user.getId(), cycle.getGroup().getId())
      .filter(Membership::isActive)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Customer is not an active member of this group"));
    Optional<Payment> existing = paymentRepository.findByMembershipIdAndCycleId(membership.getId(), cycle.getId());
    if (existing.isPresent()) return paymentView(existing.get());

    Payment payment = new Payment();
    payment.setMembership(membership);
    payment.setCycle(cycle);
    payment.setAmount(decimal(body, "amount"));
    if (payment.getAmount().signum() <= 0) throw new IllegalArgumentException("amount must be greater than zero");
    payment.setDueDate(LocalDate.parse(string(body, "dueDate")));
    payment.setMethod(body.get("method") == null ? "MANUAL" : body.get("method").toString().trim().toUpperCase(Locale.ROOT));
    PaymentStatusEnum status = paymentStatus(body.get("status"));
    payment.setStatus(status);
    payment.setPaidAt(status == PaymentStatusEnum.PAID ? OffsetDateTime.now() : null);
    Payment saved = paymentRepository.save(payment);
    if (status == PaymentStatusEnum.PAID) recordCollection(saved);
    auditService.record(authentication.getName(), "PAYMENT_RECORDED", "PAYMENT", saved.getId(),
      "{\"cycleId\":" + cycle.getId() + ",\"membershipId\":" + membership.getId() + ",\"status\":\"" + status + "\"}");
    return paymentView(saved);
  }

  @PutMapping("/api/payments/{paymentId}")
  @Transactional
  public Map<String, Object> updatePayment(@PathVariable Long paymentId, @RequestBody Map<String, Object> body, Authentication authentication) {
    Payment payment = paymentRepository.findById(paymentId)
      .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
    ownedCycle(payment.getCycle().getId(), authentication);
    PaymentStatusEnum previousStatus = payment.getStatus();
    if (body.get("method") != null && !body.get("method").toString().isBlank()) {
      payment.setMethod(body.get("method").toString().trim().toUpperCase(Locale.ROOT));
    }
    if (body.get("status") != null) payment.setStatus(paymentStatus(body.get("status")));
    if (payment.getStatus() == PaymentStatusEnum.PAID && previousStatus != PaymentStatusEnum.PAID) {
      payment.setPaidAt(OffsetDateTime.now());
      recordCollection(payment);
    }
    if (payment.getStatus() != PaymentStatusEnum.PAID) payment.setPaidAt(null);
    return paymentView(paymentRepository.save(payment));
  }

  /**
   * Admin payout history. A payout is the money paid to the member selected
   * for a cycle; it is deliberately separate from member contribution payments.
   */
  @GetMapping("/api/payouts")
  public List<Map<String, Object>> allPayouts(Authentication authentication) {
    return payoutRepository.findAll().stream()
      .filter(payout -> owns(payout.getCycle().getGroup(), authentication))
      .sorted(Comparator.comparing(Payout::getPaidAt).reversed())
      .map(this::payoutView).toList();
  }

  @GetMapping("/api/cycles/{cycleId}/payout")
  public Map<String, Object> payoutForCycle(@PathVariable Long cycleId, Authentication authentication) {
    ownedCycle(cycleId, authentication);
    Payout payout = payoutRepository.findByCycleId(cycleId)
      .orElseThrow(() -> new EntityNotFoundException("No payout has been recorded for this cycle"));
    return payoutView(payout);
  }

  /**
   * Records that the selected member was paid. The optional receipt is uploaded
   * by the API to a private GCS bucket; the browser never receives GCP credentials.
   */
  @PostMapping(value = "/api/cycles/{cycleId}/payout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Map<String, Object> recordPayout(
      @PathVariable Long cycleId,
      @RequestParam(required = false) BigDecimal amount,
      @RequestParam(defaultValue = "MANUAL") String method,
      @RequestParam(required = false) String note,
      @RequestPart(required = false) MultipartFile receipt,
      Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    Optional<Payout> existing = payoutRepository.findByCycleId(cycleId);
    if (existing.isPresent()) return payoutView(existing.get()); // safe retry after a network timeout
    if (cycle.getStatus() == CycleStatusEnum.SETTLED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This cycle was settled before payout history was enabled");
    }
    if (cycle.getWinnerMembership() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Select a claimed member before recording a payout");
    }
    if (method == null || method.isBlank()) throw new IllegalArgumentException("method is required");
    BigDecimal payoutAmount = amount == null ? cycle.getGroup().getScheme().getPotAmount() : amount;
    if (payoutAmount.signum() <= 0) throw new IllegalArgumentException("amount must be greater than zero");

    ReceiptDetails receiptDetails = uploadReceipt(cycle, receipt);
    try {
      Payout payout = new Payout();
      payout.setCycle(cycle);
      payout.setMembership(cycle.getWinnerMembership());
      payout.setAmount(payoutAmount);
      payout.setStatus(PaymentStatusEnum.PAID);
      payout.setMethod(method.trim().toUpperCase(Locale.ROOT));
      payout.setNote(note == null || note.isBlank() ? null : note.trim());
      payout.setReceiptObjectKey(receiptDetails.objectKey());
      payout.setReceiptFileName(receiptDetails.fileName());
      payout.setReceiptContentType(receiptDetails.contentType());
      payout.setPaidAt(OffsetDateTime.now());
      Payout saved = payoutRepository.save(payout);

      cycle.setStatus(CycleStatusEnum.SETTLED);
      cycle.setSettledAt(saved.getPaidAt());
      cycleRepository.save(cycle);

      LedgerEntry ledgerEntry = new LedgerEntry();
      ledgerEntry.setGroup(cycle.getGroup());
      ledgerEntry.setCycle(cycle);
      ledgerEntry.setEntryType(LedgerEntryTypeEnum.PAYOUT);
      ledgerEntry.setDirection(LedgerDirectionEnum.DEBIT);
      ledgerEntry.setAmount(saved.getAmount());
      ledgerEntry.setReferenceId(saved.getId());
      ledgerRepository.save(ledgerEntry);
      auditService.record(authentication.getName(), "PAYOUT_RECORDED", "PAYOUT", saved.getId(),
        "{\"cycleId\":" + cycleId + ",\"amount\":\"" + saved.getAmount() + "\",\"receiptUploaded\":" + (receiptDetails.objectKey() != null) + "}");
      return payoutView(saved);
    } catch (RuntimeException exception) {
      deleteReceiptQuietly(receiptDetails.objectKey());
      throw exception;
    }
  }

  /** Streams a private receipt only after verifying that the requesting admin owns the cycle. */
  @GetMapping("/api/payouts/{payoutId}/receipt")
  public ResponseEntity<InputStreamResource> downloadPayoutReceipt(@PathVariable Long payoutId, Authentication authentication) {
    Payout payout = payoutRepository.findById(payoutId)
      .orElseThrow(() -> new EntityNotFoundException("Payout not found: " + payoutId));
    ownedCycle(payout.getCycle().getId(), authentication);
    if (payout.getReceiptObjectKey() == null || payout.getReceiptObjectKey().isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No receipt was uploaded for this payout");
    }
    try {
      InputStream stream = gcpUtil.downloadFile(receiptBucket(), payout.getReceiptObjectKey());
      MediaType contentType = MediaType.parseMediaType(
        payout.getReceiptContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : payout.getReceiptContentType());
      return ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeDownloadName(payout.getReceiptFileName()) + "\"")
        .body(new InputStreamResource(stream));
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt file is unavailable", exception);
    }
  }

  @GetMapping("/api/groups/{groupId}/cycles")
  public List<Map<String, Object>> cycles(@PathVariable Long groupId, Authentication authentication) {
    ownedGroup(groupId, authentication);
    return cycleRepository.findAllByGroupIdOrderByCycleNumberDesc(groupId).stream().map(this::cycleView).toList();
  }

  @PostMapping("/api/groups/{groupId}/cycles")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createCycle(@PathVariable Long groupId, @RequestBody Map<String, Object> body, Authentication authentication) {
    ChitGroup group = ownedGroup(groupId, authentication);
    int currentCycleNumber = currentCycleNumber(group);
    if (number(body, "cycleNumber", currentCycleNumber).intValue() != currentCycleNumber) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cycles can only be created for the current month");
    if (cycleRepository.findByGroupIdAndCycleNumber(groupId, currentCycleNumber).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "The current month already has a cycle");
    Cycle cycle = new Cycle();
    cycle.setGroup(group);
    cycle.setCycleNumber(currentCycleNumber);
    cycle.setBidWindowOpenAt(dateTime(body.get("bidWindowOpenAt")));
    cycle.setBidWindowCloseAt(dateTime(body.get("bidWindowCloseAt")));
    return cycleView(cycleRepository.save(cycle));
  }

  @PostMapping("/api/groups/{groupId}/cycles/current")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createCurrentMonthCycle(@PathVariable Long groupId, Authentication authentication) {
    ChitGroup group = ownedGroup(groupId, authentication);
    int cycleNumber = currentCycleNumber(group);
    return cycleRepository.findByGroupIdAndCycleNumber(groupId, cycleNumber)
      .map(this::cycleView)
      .orElseGet(() -> {
        Cycle cycle = new Cycle();
        cycle.setGroup(group);
        cycle.setCycleNumber(cycleNumber);
        return cycleView(cycleRepository.save(cycle));
      });
  }

  @GetMapping("/api/cycles/{cycleId}")
  public Map<String, Object> cycle(@PathVariable Long cycleId, Authentication authentication) {
    return cycleView(ownedCycle(cycleId, authentication));
  }

  @PostMapping("/api/cycles/{cycleId}/open-bidding")
  public Map<String, Object> openBidding(@PathVariable Long cycleId, Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    cycle.setStatus(CycleStatusEnum.BIDDING_OPEN);
    cycle.setBidWindowOpenAt(OffsetDateTime.now());
    return cycleView(cycleRepository.save(cycle));
  }

  @PostMapping("/api/cycles/{cycleId}/close-bidding")
  public Map<String, Object> closeBidding(@PathVariable Long cycleId, Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    cycle.setStatus(CycleStatusEnum.PENDING_ADMIN_REVIEW);
    cycle.setBidWindowCloseAt(OffsetDateTime.now());
    return cycleView(cycleRepository.save(cycle));
  }

  @GetMapping("/api/groups/{groupId}/memberships")
  public List<Map<String, Object>> memberships(@PathVariable Long groupId, Authentication authentication) {
    ownedGroup(groupId, authentication);
    return membershipRepository.findAllByGroupIdOrderByJoinedAtDesc(groupId).stream().map(this::membershipView).toList();
  }

  @PostMapping("/api/groups/{groupId}/memberships")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> enroll(@PathVariable Long groupId, @RequestBody Map<String, Object> body, Authentication authentication) {
    ChitGroup group = ownedGroup(groupId, authentication);
    String username = string(body, "username");
    User user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    User actor = currentUser(authentication);
    if (!isCustomerInScope(actor, user)) {
      throw new AccessDeniedException("Customer belongs to another tenant or parent scope");
    }
    if (membershipRepository.findByUserIdAndGroupId(user.getId(), groupId).isPresent()) throw new IllegalArgumentException("Customer is already enrolled");
    Membership membership = new Membership();
    membership.setGroup(group);
    membership.setUser(user);
    return membershipView(membershipRepository.save(membership));
  }

  @DeleteMapping("/api/memberships/{membershipId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable Long membershipId, Authentication authentication) {
    Membership membership = ownedMembership(membershipId, authentication);
    membership.setActive(false);
    membershipRepository.save(membership);
  }

  @GetMapping("/api/cycles/{cycleId}/bids")
  public List<Map<String, Object>> bids(@PathVariable Long cycleId, Authentication authentication) {
    ownedCycle(cycleId, authentication);
    return bidRepository.findAllByCycleIdOrderByDiscountAmountAsc(cycleId).stream().map(this::bidView).toList();
  }

  @PostMapping("/api/cycles/{cycleId}/bids")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> bid(@PathVariable Long cycleId, @RequestBody Map<String, Object> body, Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    Membership membership = ownedMembership(number(body, "membershipId", 0).longValue(), authentication);
    Bid bid = new Bid();
    bid.setCycle(cycle);
    bid.setMembership(membership);
    bid.setDiscountAmount(decimal(body, "discountAmount"));
    Bid saved = bidRepository.save(bid);
    auditService.record(authentication.getName(), "BID_SUBMITTED", "BID", saved.getId(), "{\"discountAmount\":\"" + saved.getDiscountAmount() + "\"}");
    return bidView(saved);
  }

  @PostMapping({"/api/bids/{bidId}/approve", "/api/cycles/{cycleId}/bids/{bidId}/approve"})
  @Transactional
  public Map<String, Object> approveBid(@PathVariable Long bidId, Authentication authentication) {
    Bid bid = bidRepository.findById(bidId).orElseThrow(() -> new EntityNotFoundException("Bid not found: " + bidId));
    ownedCycle(bid.getCycle().getId(), authentication);
    finalizeWinner(bid.getCycle(), bid.getMembership(), bid, null);
    Bid saved = bidRepository.save(bid);
    auditService.record(authentication.getName(), "BID_APPROVED", "BID", saved.getId(), "{\"status\":\"WON\"}");
    return bidView(saved);
  }

  @PostMapping("/api/cycles/{cycleId}/claims/{claimId}/approve")
  @Transactional
  public Map<String, Object> approveClaim(
      @PathVariable Long cycleId,
      @PathVariable Long claimId,
      Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    Claim claim = claimRepository.findById(claimId)
      .orElseThrow(() -> new EntityNotFoundException("Claim not found: " + claimId));
    if (!cycle.getId().equals(claim.getCycle().getId())) {
      throw new IllegalArgumentException("Claim does not belong to this cycle");
    }
    ensureClaimWindowOpen(cycle);
    finalizeWinner(cycle, activeMembershipForCycle(cycle, claim.getMembership().getId()), null, claim);
    Claim saved = claimRepository.save(claim);
    auditService.record(authentication.getName(), "CLAIM_APPROVED", "CLAIM", saved.getId(), "{\"status\":\"APPROVED\"}");
    return claimView(saved);
  }

  @PostMapping("/api/cycles/{cycleId}/select-claim")
  @Transactional
  public Map<String, Object> selectClaim(
      @PathVariable Long cycleId,
      @RequestBody Map<String, Object> body,
      Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    Long membershipId = number(body, "membershipId", 0).longValue();
    Membership membership = activeMembershipForCycle(cycle, membershipId);
    Claim claim = claimRepository.findAllByCycleIdOrderBySubmittedAtDesc(cycleId).stream()
      .filter(item -> item.getMembership().getId().equals(membershipId))
      .findFirst()
      .orElseThrow(() -> new EntityNotFoundException("No claim found for this member and cycle"));
    ensureClaimWindowOpen(cycle);
    finalizeWinner(cycle, membership, null, claim);
    Claim saved = claimRepository.save(claim);
    auditService.record(authentication.getName(), "CLAIM_SELECTED", "CLAIM", saved.getId(), "{\"status\":\"APPROVED\"}");
    return claimView(saved);
  }

  @PostMapping("/api/cycles/{cycleId}/select-member")
  @Transactional
  public Map<String, Object> selectMember(
      @PathVariable Long cycleId,
      @RequestBody Map<String, Object> body,
      Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    ensureClaimWindowOpen(cycle);
    Membership membership = activeMembershipForCycle(cycle, number(body, "membershipId", 0).longValue());
    finalizeWinner(cycle, membership, null, null);
    auditService.record(authentication.getName(), "MEMBER_SELECTED", "CYCLE", cycleId, "{\"membershipId\":" + membership.getId() + "}");
    return cycleView(cycle);
  }

  @GetMapping("/api/cycles/{cycleId}/claims")
  public List<Map<String, Object>> claims(@PathVariable Long cycleId, Authentication authentication) {
    ownedCycle(cycleId, authentication);
    return claimRepository.findAllByCycleIdOrderBySubmittedAtDesc(cycleId).stream().map(this::claimView).toList();
  }

  @PostMapping("/api/cycles/{cycleId}/claims")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> claim(@PathVariable Long cycleId, @RequestBody Map<String, Object> body, Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    ensureClaimWindowOpen(cycle);
    Membership membership = activeMembershipForCycle(cycle, number(body, "membershipId", 0).longValue());
    if (claimRepository.findByCycleIdAndMembershipId(cycleId, membership.getId()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This member has already submitted a claim for the current month");
    }
    Claim claim = new Claim();
    claim.setCycle(cycle);
    claim.setMembership(membership);
    claim.setNote(body.get("note") == null ? null : body.get("note").toString().trim());
    Claim saved = claimRepository.save(claim);
    auditService.record(authentication.getName(), "CLAIM_SUBMITTED", "CLAIM", saved.getId(), "{\"status\":\"PENDING\"}");
    return claimView(saved);
  }

  @GetMapping("/api/cycles/{cycleId}/payments")
  public List<Map<String, Object>> payments(@PathVariable Long cycleId, Authentication authentication) {
    ownedCycle(cycleId, authentication);
    return paymentRepository.findAllByCycleIdOrderByDueDateDesc(cycleId).stream().map(this::paymentView).toList();
  }

  @PostMapping("/api/cycles/{cycleId}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> payment(@PathVariable Long cycleId, @RequestBody Map<String, Object> body, Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    Payment payment = new Payment();
    payment.setCycle(cycle);
    payment.setMembership(ownedMembership(number(body, "membershipId", 0).longValue(), authentication));
    payment.setAmount(decimal(body, "amount"));
    payment.setMethod(string(body, "method"));
    payment.setDueDate(LocalDate.parse(string(body, "dueDate")));
    return paymentView(paymentRepository.save(payment));
  }

  @PostMapping("/api/payments/{paymentId}/mark-paid")
  public Map<String, Object> markPaid(@PathVariable Long paymentId, Authentication authentication) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
    ownedCycle(payment.getCycle().getId(), authentication);
    if (payment.getStatus() == PaymentStatusEnum.PAID) return paymentView(payment);
    payment.setStatus(PaymentStatusEnum.PAID);
    payment.setPaidAt(OffsetDateTime.now());
    Payment saved = paymentRepository.save(payment);
    LedgerEntry collection = new LedgerEntry();
    collection.setGroup(saved.getCycle().getGroup());
    collection.setCycle(saved.getCycle());
    collection.setEntryType(LedgerEntryTypeEnum.COLLECTION);
    collection.setDirection(LedgerDirectionEnum.CREDIT);
    collection.setAmount(saved.getAmount());
    collection.setReferenceId(saved.getId());
    ledgerRepository.save(collection);
    auditService.record(authentication.getName(), "PAYMENT_MARKED_PAID", "PAYMENT", saved.getId(), "{\"status\":\"PAID\"}");
    return paymentView(saved);
  }

  @PostMapping("/api/cycles/{cycleId}/settle")
  @Transactional
  public Map<String, Object> settle(@PathVariable Long cycleId, Authentication authentication) {
    Cycle cycle = ownedCycle(cycleId, authentication);
    if (cycle.getStatus() == CycleStatusEnum.SETTLED) return cycleView(cycle);
    if (cycle.getWinnerMembership() == null) throw new IllegalStateException("A winner is required before settlement");
    Payout payoutRecord = new Payout();
    payoutRecord.setCycle(cycle);
    payoutRecord.setMembership(cycle.getWinnerMembership());
    payoutRecord.setAmount(cycle.getGroup().getScheme().getPotAmount());
    payoutRecord.setStatus(PaymentStatusEnum.PAID);
    payoutRecord.setMethod("MANUAL");
    payoutRecord.setNote("Recorded without a receipt through the legacy settlement endpoint");
    payoutRecord.setPaidAt(OffsetDateTime.now());
    Payout savedPayout = payoutRepository.save(payoutRecord);
    cycle.setStatus(CycleStatusEnum.SETTLED);
    cycle.setSettledAt(savedPayout.getPaidAt());
    cycleRepository.save(cycle);
    LedgerEntry payout = new LedgerEntry();
    payout.setGroup(cycle.getGroup());
    payout.setCycle(cycle);
    payout.setEntryType(LedgerEntryTypeEnum.PAYOUT);
    payout.setDirection(LedgerDirectionEnum.DEBIT);
    payout.setAmount(savedPayout.getAmount());
    payout.setReferenceId(savedPayout.getId());
    ledgerRepository.save(payout);
    auditService.record(authentication.getName(), "CYCLE_SETTLED", "CYCLE", cycle.getId(), "{\"status\":\"SETTLED\",\"payoutId\":" + savedPayout.getId() + ",\"payoutLedgerId\":" + payout.getId() + "}");
    return cycleView(cycle);
  }

  @GetMapping("/api/groups/{groupId}/ledger")
  public List<Map<String, Object>> ledger(@PathVariable Long groupId, Authentication authentication) {
    ownedGroup(groupId, authentication);
    return ledgerRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream().map(this::ledgerView).toList();
  }

  @GetMapping("/api/users/{username}/financial-risk")
  public Map<String, Object> financialRisk(
      @PathVariable String username,
      @RequestParam Long groupId,
      Authentication authentication) {
    ChitGroup group = ownedGroup(groupId, authentication);
    User member = userRepository.findByUsername(username)
      .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + username));
    Membership membership = membershipRepository.findByUserIdAndGroupId(member.getId(), groupId)
      .filter(Membership::isActive)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Customer is not an active member of this group"));
    List<Payment> payments = paymentRepository.findAllByMembershipIdOrderByDueDateDesc(membership.getId());
    List<Payment> groupPayments = payments.stream().filter(payment -> payment.getCycle().getGroup().getId().equals(groupId)).toList();
    long totalPayments = groupPayments.size();
    long paidPayments = groupPayments.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.PAID).count();
    long overduePayments = groupPayments.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.OVERDUE).count();
    long pendingPayments = groupPayments.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.PENDING).count();
    BigDecimal outstanding = groupPayments.stream()
      .filter(payment -> payment.getStatus() == PaymentStatusEnum.PENDING || payment.getStatus() == PaymentStatusEnum.OVERDUE)
      .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<Cycle> cycles = cycleRepository.findAllByGroupIdOrderByCycleNumberDesc(groupId);
    Cycle currentCycle = cycles.isEmpty() ? null : cycles.get(0);
    BigDecimal monthlyAmount = group.getScheme().getSchedule().stream()
      .filter(schedule -> currentCycle != null && schedule.getMonthNumber().equals(currentCycle.getCycleNumber()))
      .map(ChitSchemeSchedule::getMemberPayment).findFirst()
      .orElseGet(() -> group.getScheme().getDurationMonths() == 0 ? BigDecimal.ZERO : group.getScheme().getPotAmount().divide(BigDecimal.valueOf(group.getScheme().getDurationMonths()), 2, java.math.RoundingMode.HALF_UP));
    int score = (int) Math.min(100, overduePayments * 35 + pendingPayments * 15 + (totalPayments == 0 ? 20 : Math.round((1d - ((double) paidPayments / totalPayments)) * 30)));
    score = Math.min(100, score);
    String risk = score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW";
    String capacity = outstanding.compareTo(monthlyAmount.multiply(BigDecimal.valueOf(2))) > 0 ? "UNLIKELY" : risk.equals("HIGH") ? "REVIEW" : "LIKELY";
    List<String> reasons = new ArrayList<>();
    if (overduePayments > 0) reasons.add(overduePayments + " overdue contribution(s)");
    if (pendingPayments > 0) reasons.add(pendingPayments + " pending contribution(s)");
    if (totalPayments > 0) reasons.add(paidPayments + " of " + totalPayments + " contributions paid");
    if (reasons.isEmpty()) reasons.add("No contribution history is available yet");
    return Map.ofEntries(
      Map.entry("username", username), Map.entry("groupId", groupId), Map.entry("groupName", group.getName()),
      Map.entry("risk", risk), Map.entry("score", score), Map.entry("capacity", capacity),
      Map.entry("monthlyAmount", monthlyAmount), Map.entry("outstanding", outstanding),
      Map.entry("paidPayments", paidPayments), Map.entry("totalPayments", totalPayments),
      Map.entry("pendingPayments", pendingPayments), Map.entry("overduePayments", overduePayments),
      Map.entry("reasons", reasons));
  }

  @PostMapping("/api/users/{username}/ai-affordability")
  public Map<String, Object> aiAffordability(
      @PathVariable String username,
      @Valid @RequestBody AiAffordabilityRequest request,
      Authentication authentication) {
    Map<String, Object> transactionSummary = new LinkedHashMap<>(financialRisk(username, request.getGroupId(), authentication));
    transactionSummary.remove("username");
    transactionSummary.remove("groupId");
    transactionSummary.remove("groupName");
    Map<String, Object> advisory = vertexAiAffordabilityService.analyse(transactionSummary, request);
    return Map.of("transactionSummary", transactionSummary, "advisory", advisory);
  }

  @PostMapping("/api/groups/{groupId}/ledger")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> ledgerEntry(@PathVariable Long groupId, @RequestBody Map<String, Object> body, Authentication authentication) {
    ChitGroup group = ownedGroup(groupId, authentication);
    LedgerEntry entry = new LedgerEntry();
    entry.setGroup(group);
    entry.setEntryType(LedgerEntryTypeEnum.valueOf(string(body, "entryType").toUpperCase()));
    entry.setDirection(LedgerDirectionEnum.valueOf(string(body, "direction").toUpperCase()));
    entry.setAmount(decimal(body, "amount"));
    if (body.get("referenceId") != null) entry.setReferenceId(number(body, "referenceId", 0).longValue());
    LedgerEntry saved = ledgerRepository.save(entry);
    auditService.record(authentication.getName(), "LEDGER_ENTRY_CREATED", "LEDGER_ENTRY", saved.getId(), "{\"entryType\":\"" + saved.getEntryType() + "\",\"amount\":\"" + saved.getAmount() + "\"}");
    return ledgerView(saved);
  }

  @GetMapping("/api/analytics/summary")
  public Map<String, Object> analytics(Authentication authentication) {
    List<ChitGroup> groups = groupRepository.findAllByOrderByCreatedAtDesc().stream()
      .filter(group -> owns(group, authentication)).toList();
    Set<Long> groupIds = groups.stream().map(ChitGroup::getId).collect(java.util.stream.Collectors.toSet());
    List<Cycle> cyclesInScope = cycleRepository.findAll().stream()
      .filter(cycle -> groupIds.contains(cycle.getGroup().getId())).toList();
    List<Membership> membershipsInScope = membershipRepository.findAll().stream()
      .filter(membership -> groupIds.contains(membership.getGroup().getId())).toList();
    List<Payment> paymentsInScope = paymentRepository.findAll().stream()
      .filter(payment -> groupIds.contains(payment.getCycle().getGroup().getId())).toList();
    List<Payout> payoutsInScope = payoutRepository.findAll().stream()
      .filter(payout -> groupIds.contains(payout.getCycle().getGroup().getId())).toList();
    long ledgerEntries = ledgerRepository.findAll().stream().filter(entry -> groupIds.contains(entry.getGroup().getId())).count();
    BigDecimal totalCollections = paymentsInScope.stream()
      .filter(payment -> payment.getStatus() == PaymentStatusEnum.PAID)
      .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalPayouts = payoutsInScope.stream()
      .filter(payout -> payout.getStatus() == PaymentStatusEnum.PAID)
      .map(Payout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<Map<String, Object>> groupBreakdown = groups.stream().map(group -> {
      List<Payment> groupPayments = paymentsInScope.stream()
        .filter(payment -> payment.getCycle().getGroup().getId().equals(group.getId())).toList();
      List<Payout> groupPayouts = payoutsInScope.stream()
        .filter(payout -> payout.getCycle().getGroup().getId().equals(group.getId())).toList();
      BigDecimal collections = groupPayments.stream()
        .filter(payment -> payment.getStatus() == PaymentStatusEnum.PAID)
        .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal outstanding = groupPayments.stream()
        .filter(payment -> payment.getStatus() == PaymentStatusEnum.PENDING || payment.getStatus() == PaymentStatusEnum.OVERDUE)
        .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal payouts = groupPayouts.stream()
        .filter(payout -> payout.getStatus() == PaymentStatusEnum.PAID)
        .map(Payout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
      List<Cycle> groupCycles = cyclesInScope.stream()
        .filter(cycle -> cycle.getGroup().getId().equals(group.getId()))
        .sorted(Comparator.comparing(Cycle::getCycleNumber).reversed()).toList();
      Cycle currentCycle = groupCycles.isEmpty() ? null : groupCycles.get(0);
      List<Membership> activeMembers = membershipsInScope.stream()
        .filter(member -> member.getGroup().getId().equals(group.getId()) && member.isActive()).toList();
      List<Payment> currentCyclePayments = currentCycle == null ? List.of() : groupPayments.stream()
        .filter(payment -> payment.getCycle().getId().equals(currentCycle.getId())).toList();
      long paidMembers = currentCyclePayments.stream()
        .filter(payment -> payment.getStatus() == PaymentStatusEnum.PAID)
        .map(payment -> payment.getMembership().getId()).distinct().count();
      long totalMembers = activeMembers.size();
      long remainingMembers = Math.max(0, totalMembers - paidMembers);
      BigDecimal monthlyAmount = group.getScheme().getSchedule().stream()
        .filter(schedule -> currentCycle != null && schedule.getMonthNumber().equals(currentCycle.getCycleNumber()))
        .map(ChitSchemeSchedule::getMemberPayment).findFirst()
        .orElseGet(() -> group.getScheme().getDurationMonths() == 0
          ? BigDecimal.ZERO
          : group.getScheme().getPotAmount().divide(BigDecimal.valueOf(group.getScheme().getDurationMonths()), 2, java.math.RoundingMode.HALF_UP));
      BigDecimal currentOutstanding = monthlyAmount.multiply(BigDecimal.valueOf(remainingMembers));
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", group.getId());
      row.put("name", group.getName());
      row.put("status", group.getStatus());
      row.put("members", totalMembers);
      row.put("cycles", cyclesInScope.stream().filter(cycle -> cycle.getGroup().getId().equals(group.getId())).count());
      row.put("currentCycle", currentCycle == null ? 0 : currentCycle.getCycleNumber());
      row.put("monthlyAmount", monthlyAmount);
      row.put("paidMembers", paidMembers);
      row.put("totalMembers", totalMembers);
      row.put("remainingMembers", remainingMembers);
      row.put("payments", groupPayments.size());
      row.put("paidPayments", groupPayments.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.PAID).count());
      row.put("pendingPayments", groupPayments.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.PENDING || payment.getStatus() == PaymentStatusEnum.OVERDUE).count());
      row.put("payouts", groupPayouts.size());
      row.put("collections", collections);
      row.put("outstanding", currentOutstanding);
      row.put("payoutAmount", payouts);
      row.put("netResult", collections.subtract(payouts));
      return row;
    }).toList();
    BigDecimal totalOutstanding = groupBreakdown.stream()
      .map(row -> (BigDecimal) row.get("outstanding"))
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("groups", groups.size());
    result.put("cycles", cyclesInScope.size());
    result.put("memberships", membershipsInScope.stream().filter(Membership::isActive).count());
    result.put("payments", paymentsInScope.size());
    result.put("paidPayments", paymentsInScope.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.PAID).count());
    result.put("pendingPayments", paymentsInScope.stream().filter(payment -> payment.getStatus() == PaymentStatusEnum.PENDING || payment.getStatus() == PaymentStatusEnum.OVERDUE).count());
    result.put("payouts", payoutsInScope.stream().filter(payout -> payout.getStatus() == PaymentStatusEnum.PAID).count());
    result.put("collections", totalCollections);
    result.put("outstanding", totalOutstanding);
    result.put("totalPayouts", totalPayouts);
    result.put("netResult", totalCollections.subtract(totalPayouts));
    result.put("payoutsWithReceipts", payoutsInScope.stream().filter(payout -> payout.getReceiptObjectKey() != null).count());
    result.put("ledgerEntries", ledgerEntries);
    result.put("groupBreakdown", groupBreakdown);
    return result;
  }

  private ChitGroup ownedGroup(Long id, Authentication authentication) {
    ChitGroup group = groupRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    if (!owns(group, authentication)) throw new AccessDeniedException("Group is not owned by this admin");
    return group;
  }
  private boolean owns(ChitGroup group, Authentication authentication) {
    User actor = currentUser(authentication);
    User owner = group.getScheme().getAdmin();
    return actor.getId().equals(owner.getId())
      && actor.getTenantId() != null
      && actor.getTenantId().equals(owner.getTenantId());
  }
  private User currentUser(Authentication authentication) {
    return userRepository.findByUsername(authentication.getName())
      .orElseThrow(() -> new EntityNotFoundException("User not found: " + authentication.getName()));
  }
  private boolean isCustomerInScope(User actor, User customer) {
    return actor.getTenantId() != null
      && actor.getTenantId().equals(customer.getTenantId())
      && customer.getParent() != null
      && actor.getId().equals(customer.getParent().getId());
  }
  private Cycle ownedCycle(Long id, Authentication authentication) { Cycle cycle = cycleRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + id)); ownedGroup(cycle.getGroup().getId(), authentication); return cycle; }
  private Membership ownedMembership(Long id, Authentication authentication) { Membership membership = membershipRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Membership not found: " + id)); ownedGroup(membership.getGroup().getId(), authentication); return membership; }
  private Membership activeMembershipForCycle(Cycle cycle, Long membershipId) {
    Membership membership = membershipRepository.findById(membershipId)
      .orElseThrow(() -> new EntityNotFoundException("Membership not found: " + membershipId));
    if (!membership.isActive() || !membership.getGroup().getId().equals(cycle.getGroup().getId())) {
      throw new IllegalArgumentException("Member is not active in this chit group");
    }
    return membership;
  }
  private int currentCycleNumber(ChitGroup group) {
    if (group.getStartDate() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Set the group start date before creating a monthly cycle");
    long elapsedMonths = ChronoUnit.MONTHS.between(YearMonth.from(group.getStartDate()), YearMonth.now());
    int cycleNumber = Math.toIntExact(elapsedMonths + 1);
    if (cycleNumber < 1 || cycleNumber > group.getScheme().getDurationMonths()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This group is not active for the current month");
    }
    return cycleNumber;
  }
  private void ensureClaimWindowOpen(Cycle cycle) {
    if (cycle.getStatus() != CycleStatusEnum.BIDDING_OPEN) throw new ResponseStatusException(HttpStatus.CONFLICT, "Claims are not open for this cycle");
    OffsetDateTime now = OffsetDateTime.now();
    if (cycle.getBidWindowOpenAt() != null && now.isBefore(cycle.getBidWindowOpenAt())) throw new ResponseStatusException(HttpStatus.CONFLICT, "The claim window has not opened");
    if (cycle.getBidWindowCloseAt() != null && now.isAfter(cycle.getBidWindowCloseAt())) throw new ResponseStatusException(HttpStatus.CONFLICT, "The claim window has closed");
  }
  private void finalizeWinner(Cycle cycle, Membership winner, Bid winningBid, Claim winningClaim) {
    if (cycle.getWinnerMembership() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "A member has already been selected for this month");
    bidRepository.findAllByCycleIdOrderByDiscountAmountAsc(cycle.getId()).forEach(bid -> {
      bid.setStatus(bid == winningBid ? BidStatusEnum.WON : BidStatusEnum.LOST);
      bidRepository.save(bid);
    });
    claimRepository.findAllByCycleIdOrderBySubmittedAtDesc(cycle.getId()).forEach(claim -> {
      claim.setStatus(claim == winningClaim ? ClaimStatusEnum.APPROVED : ClaimStatusEnum.REJECTED);
      claimRepository.save(claim);
    });
    winner.setHasWon(true);
    membershipRepository.save(winner);
    cycle.setWinnerMembership(winner);
    cycle.setStatus(CycleStatusEnum.PENDING_ADMIN_REVIEW);
    cycle.setBidWindowCloseAt(OffsetDateTime.now());
    cycleRepository.save(cycle);
    winnerNotificationService.notifyWinner(cycle);
  }
  private String string(Map<String, Object> body, String key) { Object value = body.get(key); if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(key + " is required"); return value.toString(); }
  private Number number(Map<String, Object> body, String key, Number fallback) { Object value = body.get(key); return value instanceof Number ? (Number) value : value == null ? fallback : Long.valueOf(value.toString()); }
  private BigDecimal decimal(Map<String, Object> body, String key) { return new BigDecimal(string(body, key)); }
  private OffsetDateTime dateTime(Object value) { return value == null ? null : OffsetDateTime.parse(value.toString()); }
  private Map<String, Object> cycleView(Cycle c) {
    Membership winner = c.getWinnerMembership();
    return Map.ofEntries(
      Map.entry("id", c.getId()),
      Map.entry("groupId", c.getGroup().getId()),
      Map.entry("groupName", c.getGroup().getName()),
      Map.entry("cycleNumber", c.getCycleNumber()),
      Map.entry("status", c.getStatus()),
      Map.entry("winnerMembershipId", winner == null ? 0 : winner.getId()),
      Map.entry("winnerUsername", winner == null ? "" : winner.getUser().getUsername()),
      Map.entry("winnerName", winner == null || winner.getUser().getDisplayName() == null ? "" : winner.getUser().getDisplayName()),
      Map.entry("payoutRecorded", payoutRepository.findByCycleId(c.getId()).isPresent()),
      Map.entry("bidWindowOpenAt", c.getBidWindowOpenAt() == null ? "" : c.getBidWindowOpenAt()),
      Map.entry("bidWindowCloseAt", c.getBidWindowCloseAt() == null ? "" : c.getBidWindowCloseAt())
    );
  }
  private Map<String, Object> membershipView(Membership m) { return Map.of("id", m.getId(), "userId", m.getUser().getId(), "username", m.getUser().getUsername(), "displayName", m.getUser().getDisplayName(), "groupId", m.getGroup().getId(), "active", m.isActive(), "hasWon", m.isHasWon()); }
  private Map<String, Object> bidView(Bid b) { return Map.of("id", b.getId(), "cycleId", b.getCycle().getId(), "membershipId", b.getMembership().getId(), "username", b.getMembership().getUser().getUsername(), "discountAmount", b.getDiscountAmount(), "status", b.getStatus()); }
  private Map<String, Object> claimView(Claim c) { return Map.of("id", c.getId(), "cycleId", c.getCycle().getId(), "membershipId", c.getMembership().getId(), "username", c.getMembership().getUser().getUsername(), "note", c.getNote() == null ? "" : c.getNote(), "status", c.getStatus()); }
  private Map<String, Object> paymentView(Payment p) { return Map.of("id", p.getId(), "cycleId", p.getCycle().getId(), "membershipId", p.getMembership().getId(), "username", p.getMembership().getUser().getUsername(), "amount", p.getAmount(), "status", p.getStatus(), "method", p.getMethod() == null ? "" : p.getMethod(), "dueDate", p.getDueDate(), "paidAt", p.getPaidAt() == null ? "" : p.getPaidAt()); }

  private PaymentStatusEnum paymentStatus(Object value) {
    return value == null || value.toString().isBlank()
      ? PaymentStatusEnum.PAID
      : PaymentStatusEnum.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
  }

  private void recordCollection(Payment payment) {
    LedgerEntry ledgerEntry = new LedgerEntry();
    ledgerEntry.setGroup(payment.getCycle().getGroup());
    ledgerEntry.setCycle(payment.getCycle());
    ledgerEntry.setEntryType(LedgerEntryTypeEnum.COLLECTION);
    ledgerEntry.setDirection(LedgerDirectionEnum.CREDIT);
    ledgerEntry.setAmount(payment.getAmount());
    ledgerEntry.setReferenceId(payment.getId());
    ledgerRepository.save(ledgerEntry);
  }
  private Map<String, Object> payoutView(Payout p) { return Map.ofEntries(
    Map.entry("id", p.getId()), Map.entry("cycleId", p.getCycle().getId()), Map.entry("cycleNumber", p.getCycle().getCycleNumber()),
    Map.entry("groupId", p.getCycle().getGroup().getId()), Map.entry("membershipId", p.getMembership().getId()),
    Map.entry("username", p.getMembership().getUser().getUsername()), Map.entry("amount", p.getAmount()), Map.entry("status", p.getStatus()),
    Map.entry("method", p.getMethod()), Map.entry("note", p.getNote() == null ? "" : p.getNote()), Map.entry("paidAt", p.getPaidAt()),
    Map.entry("receiptFileName", p.getReceiptFileName() == null ? "" : p.getReceiptFileName()),
    Map.entry("receiptAvailable", p.getReceiptObjectKey() != null && !p.getReceiptObjectKey().isBlank()),
    Map.entry("receiptUrl", p.getReceiptObjectKey() == null || p.getReceiptObjectKey().isBlank() ? "" : "/api/payouts/" + p.getId() + "/receipt")
  ); }
  private Map<String, Object> ledgerView(LedgerEntry e) { return Map.of("id", e.getId(), "groupId", e.getGroup().getId(), "cycleId", e.getCycle() == null ? 0 : e.getCycle().getId(), "entryType", e.getEntryType(), "direction", e.getDirection(), "amount", e.getAmount(), "referenceId", e.getReferenceId() == null ? 0 : e.getReferenceId()); }

  private ReceiptDetails uploadReceipt(Cycle cycle, MultipartFile receipt) {
    if (receipt == null || receipt.isEmpty()) return ReceiptDetails.empty();
    if (receipt.getSize() > MAX_RECEIPT_SIZE_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Receipt must be 10 MB or smaller");
    }
    String contentType = receipt.getContentType();
    if (contentType == null || !ALLOWED_RECEIPT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Receipt must be a PDF, JPEG, PNG, or WebP file");
    }
    String fileName = safeFileName(receipt.getOriginalFilename());
    String objectKey = "payout-receipts/cycle-" + cycle.getId() + "/" + UUID.randomUUID() + "-" + fileName;
    try {
      if (!gcpUtil.uploadFileToGcsBucket(receiptBucket(), objectKey, receipt)) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Receipt upload failed");
      }
      return new ReceiptDetails(objectKey, fileName, contentType.toLowerCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to upload receipt", exception);
    }
  }

  private String receiptBucket() {
    String bucket = gcpProperties.getStorage().getReceiptUploadBucket();
    if (bucket == null || bucket.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Receipt storage is not configured");
    return bucket;
  }

  private void deleteReceiptQuietly(String objectKey) {
    if (objectKey != null) gcpUtil.deleteGcsObject(receiptBucket(), objectKey);
  }

  private String safeFileName(String originalFileName) {
    String name = originalFileName == null ? "receipt" : originalFileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    name = name.replaceAll("\\s+", "_");
    if (name.isBlank()) name = "receipt";
    return name.length() > 120 ? name.substring(name.length() - 120) : name;
  }

  private String safeDownloadName(String fileName) {
    return safeFileName(fileName).replace("\"", "_");
  }

  private record ReceiptDetails(String objectKey, String fileName, String contentType) {
    private static ReceiptDetails empty() { return new ReceiptDetails(null, null, null); }
  }
}
