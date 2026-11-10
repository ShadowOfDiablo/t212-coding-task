package com.tihomir.t212.tradingbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("training")
class T212SpringBootProjectApplicationTests {

  @Test
  void contextLoads() {
    System.out.println("T212SpringBootProjectApplicationTests started");
  }
}
