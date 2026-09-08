package com.ms.chitcircle.apis;

import com.ms.chitcircle.dtos.CreateGroupRequest;
import com.ms.chitcircle.dtos.GroupDto;
import com.ms.chitcircle.dtos.UpdateGroupRequest;
import com.ms.chitcircle.services.ChitGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class ChitGroupApi {
  private final ChitGroupService groupService;

  @PostMapping
  public ResponseEntity<GroupDto> create(
      @Valid @RequestBody CreateGroupRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(groupService.create(authentication.getName(), request));
  }

  @GetMapping
  public List<GroupDto> list(@RequestParam(required = false) Long schemeId, Authentication authentication) {
    return schemeId == null ? groupService.listAll(authentication.getName()) : groupService.list(authentication.getName(), schemeId);
  }

  @GetMapping("/{id}")
  public GroupDto get(@PathVariable Long id, Authentication authentication) {
    return groupService.get(authentication.getName(), id);
  }

  @PutMapping("/{id}")
  public GroupDto update(@PathVariable Long id, @RequestBody UpdateGroupRequest request,
                         Authentication authentication) {
    return groupService.update(authentication.getName(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, Authentication authentication) {
    groupService.delete(authentication.getName(), id);
  }
}
