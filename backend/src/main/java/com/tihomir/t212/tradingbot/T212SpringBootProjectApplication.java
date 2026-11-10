package com.tihomir.t212.tradingbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class T212SpringBootProjectApplication {

  public static void main(String[] args) {
    SpringApplication.run(T212SpringBootProjectApplication.class, args);
    System.out.println("T212SpringBootProjectApplication started");
  }
}
