package com.ms.chitcircle.dtos.secret;

import lombok.Data;

@Data
public class HikariSecret {

  private String poolName; // Name for the connection pool
  private int minimumIdle; // Minimum number of idle connections
  private int maximumPoolSize; // Maximum number of connections in the pool
  private long idleTimeout; // Time (ms) a connection can be idle before being removed
  private long maxLifetime; // Maximum lifetime (ms) of a connection in the pool
  private long connectionTimeout; // Maximum time (ms) to wait for a connection from the pool
  private long validationTimeout; // Maximum time (ms) for a connection validation
  private long initializationFailTimeout; // Fail fast if the connection pool cannot initialize
  private long leakDetectionThreshold; // Time (ms) before a connection is considered a leak
  private boolean readOnly; // Set connections as read-only
  private boolean allowPoolSuspension; // Allow the pool to be suspended or resumed
  private String connectionInitSql; // SQL query to run when a connection is initialized
}
