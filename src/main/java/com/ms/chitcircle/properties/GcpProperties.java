package com.ms.chitcircle.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gcp")
@Getter
@Setter
public class GcpProperties {
  private String projectId;
  private String region;
  private Storage storage = new Storage();
  private Secrets secrets = new Secrets();
  private Email email = new Email();

  @Getter
  @Setter
  public static class Storage {
    private String imageUploadBucket;
    private String receiptUploadBucket;
    private String kycUploadBucket;
    private String objectKeyPrefix = "";
  }

  @Getter
  @Setter
  public static class Secrets {
    private String database;
    private String hikari;
    private String paseto;
    private String gmailOauth;
    private String vertexAi;
  }

  @Getter
  @Setter
  public static class Email {
    private String sender = "sreekeerthimaripally@gmail.com";
    private boolean enabled = true;
  }
}
