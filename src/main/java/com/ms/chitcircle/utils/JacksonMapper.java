package com.ms.chitcircle.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
@NoArgsConstructor
public class JacksonMapper {

  private static final ObjectMapper objectMapper = createDefaultMapper();

  @Bean
  public static ObjectMapper getInstance() {
    return objectMapper;
  }

  private static ObjectMapper createDefaultMapper() {
    ObjectMapper mapper = new ObjectMapper();

    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new Hibernate6Module());

    mapper.addMixIn(Object.class, HibernateLazyInitializerMixin.class);

    return mapper;
  }

  @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
  private abstract static class HibernateLazyInitializerMixin {}

  public static <T> String toJson(T object) throws JsonProcessingException {
    return objectMapper.writeValueAsString(object);
  }

  public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
    return objectMapper.readValue(json, clazz);
  }

  public static <X extends Throwable, T> T fromJsonOrElseThrow(
    String json,
    Class<T> clazz,
    Supplier<? extends X> exceptionSupplier) throws X {

    try {
      return objectMapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw exceptionSupplier.get();
    }
  }
}
