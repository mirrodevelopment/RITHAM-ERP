package com.ritham.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.ritham.erp.common.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class RithamErpApplication {

	public static void main(String[] args) {
		SpringApplication.run(RithamErpApplication.class, args);
	}

}
