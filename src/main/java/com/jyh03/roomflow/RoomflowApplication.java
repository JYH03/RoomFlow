package com.jyh03.roomflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RoomflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoomflowApplication.class, args);
	}

}
