package com.galaxy.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.galaxy.sim")
public class GalaxySimApplication {

	public static void main(String[] args) {
		SpringApplication.run(GalaxySimApplication.class, args);
	}

}
