package com.ms.chitcircle.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.ms.chitcircle.dtos.AiAffordabilityRequest;
import com.ms.chitcircle.properties.GcpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VertexAiAffordabilityService {
  private static final String VERTEX_AI_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final GcpProperties gcpProperties;
  private final JsonMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public Map<String, Object> analyse(Map<String, Object> transactionSummary, AiAffordabilityRequest request) {
    try {
      String accessToken = getAccessToken();
      String prompt = "You are an affordability advisory assistant for a regulated chit-fund administrator. "
        + "Do not make a final approval/rejection or infer protected traits. Return strict JSON only with fields "
        + "recommendation (CONSIDER, MANUAL_REVIEW, or NOT_ADVISABLE), confidence (LOW, MEDIUM, or HIGH), "
        + "summary (max 60 words), strengths (array of max 3 strings), concerns (array of max 3 strings), "
        + "followUpQuestions (array of max 3 strings), and disclaimer. Use only these anonymized figures and answers: "
        + objectMapper.writeValueAsString(Map.of(
          "transactionHistory", transactionSummary,
          "questionnaire", Map.of(
            "monthlyIncome", request.getMonthlyIncome(), "monthlyObligations", request.getMonthlyObligations(),
            "emergencySavings", request.getEmergencySavings(), "dependents", request.getDependents(),
            "employmentType", request.getEmploymentType(), "employmentMonths", request.getEmploymentMonths(),
            "notes", request.getNotes() == null ? "" : request.getNotes())));
      Map<String, Object> body = Map.of(
        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
        "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json"));
      String region = gcpProperties.getRegion();
      String projectId = gcpProperties.getProjectId();
      String endpoint = "https://" + region + "-aiplatform.googleapis.com/v1/projects/" + projectId
        + "/locations/" + region + "/publishers/google/models/gemini-2.5-flash:generateContent";
      HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error("Vertex AI error: status={}, body={}", response.statusCode(), response.body());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vertex AI could not complete the affordability analysis");
      }
      JsonNode root = objectMapper.readTree(response.body());
      String result = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString();
      if (result.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vertex AI returned no affordability analysis");
      return objectMapper.readValue(result, Map.class);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vertex AI affordability analysis failed", exception);
    }
  }

  private String getAccessToken() {
    try {
      GoogleCredentials credentials = GoogleCredentials
        .getApplicationDefault()
        .createScoped(Collections.singleton(VERTEX_AI_SCOPE));
      credentials.refreshIfExpired();
      return credentials.getAccessToken().getTokenValue();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not obtain GCP credentials for Vertex AI", e);
    }
  }
}
