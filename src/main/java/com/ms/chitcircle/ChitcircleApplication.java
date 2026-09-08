package com.ms.chitcircle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChitcircleApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChitcircleApplication.class, args);
	}

}
