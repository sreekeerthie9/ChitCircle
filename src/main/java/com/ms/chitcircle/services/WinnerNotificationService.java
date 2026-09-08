package com.ms.chitcircle.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.services.gmail.model.Message;
import com.google.auth.oauth2.UserCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.ms.chitcircle.dtos.secret.GmailOauthSecret;
import com.ms.chitcircle.models.Cycle;
import com.ms.chitcircle.models.Membership;
import com.ms.chitcircle.models.Notification;
import com.ms.chitcircle.properties.GcpProperties;
import com.ms.chitcircle.repositories.NotificationRepository;
import com.ms.chitcircle.utils.GcpUtil;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class WinnerNotificationService {
  private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

  private final NotificationRepository notificationRepository;
  private final GcpProperties gcpProperties;
  private final GcpUtil gcpUtil;

  public void notifyWinner(Cycle cycle) {
    Membership winner = cycle.getWinnerMembership();
    if (winner == null || winner.getUser() == null) return;

    String winnerName = winner.getUser().getDisplayName();
    if (winnerName == null || winnerName.isBlank()) winnerName = winner.getUser().getUsername();
    String groupName = cycle.getGroup().getName();
    String message = "Congratulations " + winnerName + "! You were selected as the winner for cycle "
      + cycle.getCycleNumber() + " of " + groupName + ".";

    Notification notification = new Notification();
    notification.setUser(winner.getUser());
    notification.setType("BID_RESULT");
    notification.setPayload("{\"title\":\"Bid result\",\"message\":\""
      + jsonEscape(message) + "\",\"cycleId\":" + cycle.getId()
      + ",\"groupName\":\"" + jsonEscape(groupName) + "\"}");
    notification.setSentAt(OffsetDateTime.now());
    notificationRepository.save(notification);

    if (!gcpProperties.getEmail().isEnabled() || winner.getUser().getEmail() == null
      || winner.getUser().getEmail().isBlank()) {
      log.warn("Winner notification email skipped for cycle {}: recipient email is unavailable or email is disabled", cycle.getId());
      return;
    }

    try {
      sendEmail(winner.getUser().getEmail(), winnerName, groupName, cycle.getCycleNumber());
    } catch (Exception exception) {
      log.error("Winner notification email failed for cycle {}", cycle.getId(), exception);
    }
  }

  private void sendEmail(String recipient, String winnerName, String groupName, Integer cycleNumber) throws Exception {
    GmailOauthSecret secret = gcpUtil.getSecret(
      gcpProperties.getSecrets().getGmailOauth(), GmailOauthSecret.class);
    GoogleCredentials credentials = UserCredentials.newBuilder()
      .setClientId(secret.getClientId())
      .setClientSecret(secret.getClientSecret())
      .setRefreshToken(secret.getRefreshToken())
      .build()
      .createScoped(GmailScopes.GMAIL_SEND);
    credentials.refreshIfExpired();

    Gmail gmail = new Gmail.Builder(
      GoogleNetHttpTransport.newTrustedTransport(),
      JSON_FACTORY,
      new HttpCredentialsAdapter(credentials))
      .setApplicationName("ChitCircle")
      .build();

    MimeMessage email = new MimeMessage(Session.getInstance(new Properties()));
    email.setFrom(new InternetAddress(gcpProperties.getEmail().getSender()));
    email.setRecipient(RecipientType.TO, new InternetAddress(recipient));
    email.setSubject("You won a ChitCircle bid");
    email.setText("Congratulations " + winnerName + "!\n\nYou were selected as the winner for cycle "
      + cycleNumber + " of " + groupName + ".\n\nChitCircle");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    email.writeTo(output);
    Message gmailMessage = new Message()
      .setRaw(Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray()));
    gmail.users().messages().send("me", gmailMessage).execute();
  }

  private String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
      .replace("\r", "\\r").replace("\n", "\\n");
  }
}