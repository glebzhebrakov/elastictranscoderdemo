package com.glebzhebrakov.elastictranscoderdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

//@SpringCloudAutoConfigure
@SpringBootApplication
@EnableContextInstanceData
@EnableAutoConfiguration
public class ElastictranscoderdemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElastictranscoderdemoApplication.class, args);
	}
}
