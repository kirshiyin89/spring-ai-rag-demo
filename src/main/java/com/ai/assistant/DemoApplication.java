package com.ai.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.ai.assistant.config")
public class DemoApplication {

  static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }

}
