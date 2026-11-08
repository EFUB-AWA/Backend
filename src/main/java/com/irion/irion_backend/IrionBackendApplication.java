package com.irion.irion_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IrionBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(IrionBackendApplication.class, args);
	}

}
