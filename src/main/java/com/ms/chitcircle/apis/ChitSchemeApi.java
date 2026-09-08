package com.ms.chitcircle.apis;

import com.ms.chitcircle.dtos.CreateSchemeRequest;
import com.ms.chitcircle.dtos.SchemeDto;
import com.ms.chitcircle.dtos.UpdateSchemeRequest;
import com.ms.chitcircle.dtos.BulkSchemeScheduleRequest;
import com.ms.chitcircle.dtos.UpdateSchemeScheduleRequest;
import com.ms.chitcircle.services.ChitSchemeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schemes")
@RequiredArgsConstructor
public class ChitSchemeApi {
  private final ChitSchemeService schemeService;

  @PostMapping
  public ResponseEntity<SchemeDto> create(
      @Valid @RequestBody CreateSchemeRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(schemeService.create(authentication.getName(), request));
  }

  @GetMapping
  public List<SchemeDto> list(Authentication authentication) {
    return schemeService.list(authentication.getName());
  }

  @GetMapping("/{id}")
  public SchemeDto get(@PathVariable Long id, Authentication authentication) {
    return schemeService.get(authentication.getName(), id);
  }

  @PutMapping("/{id}")
  public SchemeDto update(@PathVariable Long id, @Valid @RequestBody UpdateSchemeRequest request,
                          Authentication authentication) {
    return schemeService.update(authentication.getName(), id, request);
  }

  @PostMapping("/{id}/schedule")
  public SchemeDto updateSchedule(@PathVariable Long id, @Valid @RequestBody UpdateSchemeScheduleRequest request,
                                  Authentication authentication) {
    return schemeService.updateSchedule(authentication.getName(), id, request);
  }

  @PostMapping("/schedule/bulk")
  public SchemeDto bulkSchedule(@Valid @RequestBody BulkSchemeScheduleRequest request,
                                Authentication authentication) {
    return schemeService.bulkUpdateSchedule(authentication.getName(), request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, Authentication authentication) {
    schemeService.delete(authentication.getName(), id);
  }
}
