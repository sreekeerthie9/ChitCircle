package com.ms.chitcircle.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import java.util.function.Supplier;

@Configuration
@NoArgsConstructor
public class JacksonMapper {

  private static final JsonMapper objectMapper = createDefaultMapper();

  @Bean
  public static JsonMapper getInstance() {
    return objectMapper;
  }

  private static JsonMapper createDefaultMapper() {
    return JsonMapper.builder()
      .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .addModule(new Hibernate7Module())
      .addMixIn(Object.class, HibernateLazyInitializerMixin.class)
      .build();
  }

  @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
  private abstract static class HibernateLazyInitializerMixin {}

  public static <T> String toJson(T object) {
    return objectMapper.writeValueAsString(object);
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return objectMapper.readValue(json, clazz);
  }

  public static <X extends RuntimeException, T> T fromJsonOrElseThrow(
    String json,
    Class<T> clazz,
    Supplier<? extends X> exceptionSupplier) {

    try {
      return objectMapper.readValue(json, clazz);
    } catch (JacksonException e) {
      throw exceptionSupplier.get();
    }
  }
}