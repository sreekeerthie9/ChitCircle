package com.ms.chitcircle.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HashUtil {
  private HashUtil() {}

  public static String sha256(String input) {
    if (input == null) {
      return null;
    }
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }

  public static boolean matchesSha256(String rawInput, String hash) {
    if (rawInput == null || hash == null) {
      return false;
    }
    return sha256(rawInput).equalsIgnoreCase(hash);
  }
}
