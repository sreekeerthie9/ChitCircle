package com.ms.chitcircle.dtos.secret;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.paseto4j.commons.SecretKey;
import org.paseto4j.commons.Version;

import java.nio.charset.StandardCharsets;

@Data
public class PasetoSecret {

  private String key;
  private String footer;

  @JsonIgnore
  public SecretKey getSecretKey() {
    return new SecretKey(this.getKey().getBytes(StandardCharsets.UTF_8), Version.V4);
  }
}
