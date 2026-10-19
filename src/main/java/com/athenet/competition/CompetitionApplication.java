package com.athenet.competition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CompetitionApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompetitionApplication.class, args);
	}

}
