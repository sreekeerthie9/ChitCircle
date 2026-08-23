package com.ms.chitcircle.config;

import com.ms.chitcircle.dtos.secret.HikariSecret;
import com.ms.chitcircle.dtos.secret.PostgresSecret;
import com.ms.chitcircle.mappers.HikariMapper;
import com.ms.chitcircle.properties.GcpProperties;
import com.ms.chitcircle.utils.GcpUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

import javax.sql.DataSource;
import java.text.MessageFormat;

@Configuration
public class PostgresConfig {
  private final GcpProperties gcpProperties;
  private final GcpUtil gcpUtil;
  private final HikariMapper hikariMapper;

  @Autowired
  public PostgresConfig(GcpProperties gcpProperties, GcpUtil gcpUtil, HikariMapper hikariMapper) {
    this.gcpProperties = gcpProperties;
    this.gcpUtil = gcpUtil;
    this.hikariMapper = hikariMapper;
  }

  @Bean
  public DataSource postgresDataSource() {
    PostgresSecret secret =
      gcpUtil.getSecret(gcpProperties.getSecrets().getDatabase(), PostgresSecret.class);
    HikariSecret hikariSecret =
      gcpUtil.getSecret(gcpProperties.getSecrets().getHikari(), HikariSecret.class);
    HikariConfig hikariConfig = hikariMapper.secretToConfig(hikariSecret);

    String jdbcUrl = MessageFormat.format(
      "jdbc:postgresql://{0}:{1}/{2}", secret.getHost(), secret.getPort(), secret.getDatabase());

    // Use the HikariConfig bound to the YAML properties
    hikariConfig.setJdbcUrl(jdbcUrl);
    hikariConfig.setUsername(secret.getUsername());
    hikariConfig.setPassword(secret.getPassword());
    hikariConfig.setDriverClassName(secret.getDriver());

    return new HikariDataSource(hikariConfig);
  }

  @Bean
  public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}
