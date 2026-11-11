package com.tihomir.t212.tradingbot;

import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class T212SpringBootProjectApplicationTests {

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Test
  void contextLoads() {
    System.out.println("T212SpringBootProjectApplicationTests started");
  }
}
