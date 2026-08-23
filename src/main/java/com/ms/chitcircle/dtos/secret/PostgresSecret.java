package com.ms.chitcircle.dtos.secret;

import lombok.Data;

@Data
public class PostgresSecret {

  private String username;
  private String password;
  private String host;
  private String port;
  private String database;
  private String driver;
}
