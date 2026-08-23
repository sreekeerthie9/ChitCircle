package com.ms.chitcircle.config;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GcpConfig {

  @Bean
  public Storage storage() {
    return StorageOptions.getDefaultInstance().getService();
  }

  @Bean(destroyMethod = "close")
  public SecretManagerServiceClient secretManagerServiceClient() throws IOException {
    return SecretManagerServiceClient.create();
  }
}
