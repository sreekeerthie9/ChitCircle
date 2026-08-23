package com.ms.chitcircle.mappers;

import com.ms.chitcircle.dtos.secret.HikariSecret;
import com.zaxxer.hikari.HikariConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HikariMapper {

  HikariConfig secretToConfig(HikariSecret secret);
}

