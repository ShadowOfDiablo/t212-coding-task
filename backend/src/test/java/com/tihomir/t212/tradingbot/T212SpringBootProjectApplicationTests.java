package com.tihomir.t212.tradingbot;

import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class T212SpringBootProjectApplicationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Test
  void contextLoads() {
    System.out.println("T212SpringBootProjectApplicationTests started");
  }
}
