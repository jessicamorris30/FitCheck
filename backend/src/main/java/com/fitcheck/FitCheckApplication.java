package com.fitcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // finds @ConfigurationProperties records such as config.JwtProperties
public class FitCheckApplication {

	public static void main(String[] args) {
		SpringApplication.run(FitCheckApplication.class, args);
	}

}
