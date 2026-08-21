package com.rishabh.game_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class GamePlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(GamePlatformApplication.class, args);
	}

}
