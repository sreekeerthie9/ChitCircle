package com.ms.chitcircle.constants;

import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.TemporalAmount;

public final class Constants {
  public static final String AUTH_TOKEN_NAME = "authToken";
  public static final String REFRESH_TOKEN_NAME = "refreshToken";

  public static final String IST_ZONE = "Asia/Kolkata";

  public static final ZoneId DEFAULT_DATE_ZONE_ID = ZoneId.of("Asia/Kolkata");

  public static final TemporalAmount AUTH_TOKEN_EXPIRATION_TIME = Duration.ofHours(10000);
  public static final TemporalAmount REFRESH_TOKEN_EXPIRATION_TIME = Duration.ofDays(2);
}
