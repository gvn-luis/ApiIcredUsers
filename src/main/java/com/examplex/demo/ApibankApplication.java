package com.examplex.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // ← ADICIONAR ESTA ANOTAÇÃO
public class ApibankApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApibankApplication.class, args);
	}
}