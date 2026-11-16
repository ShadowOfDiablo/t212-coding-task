package com.tihomir.t212.tradingbot.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.account.AccountReset;
import com.tihomir.t212.tradingbot.account.AccountResetList;
import com.tihomir.t212.tradingbot.account.AccountResetService;
import com.tihomir.t212.tradingbot.account.EmptiedAccount;
import com.tihomir.t212.tradingbot.account.value.AccountValuePoint;
import com.tihomir.t212.tradingbot.account.value.AccountValuePointList;
import com.tihomir.t212.tradingbot.account.value.AccountValueService;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotRunningException;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

  private static final String ACCOUNT = "/api/brokers/binance/account";

  @Autowired MockMvc mockMvc;

  @MockitoBean AccountResetService accountResetService;
  @MockitoBean AccountValueService accountValueService;

  @Test
  void emptiesTheAccountAndAnswersConflictWhileTheBotRuns() throws Exception {
    when(accountResetService.emptyAccount("binance"))
        .thenReturn(
            new EmptiedAccount(
                List.of(new AssetBalance("USDC", new BigDecimal("5000"), BigDecimal.ZERO)),
                List.of(),
                2))
        .thenThrow(new LiveBotRunningException("binance"));

    mockMvc
        .perform(post(ACCOUNT + "/reset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.soldBalances[0].asset").value("USDC"))
        .andExpect(jsonPath("$.closedPositionCount").value(2));
    mockMvc.perform(post(ACCOUNT + "/reset")).andExpect(status().isConflict());
  }

  @Test
  void listsTheResetTimes() throws Exception {
    when(accountResetService.getAccountResets("binance"))
        .thenReturn(
            new AccountResetList(List.of(new AccountReset(Instant.parse("2026-11-16T09:30:00Z")))));

    mockMvc
        .perform(get(ACCOUNT + "/resets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resetTimes[0]").value("2026-11-16T09:30:00Z"));
  }

  @Test
  void listsTheAccountValuePoints() throws Exception {
    when(accountValueService.getAccountValuePoints("binance"))
        .thenReturn(
            new AccountValuePointList(
                List.of(
                    new AccountValuePoint(
                        Instant.parse("2026-11-16T12:00:00Z"), new BigDecimal("10004.5")))));

    mockMvc
        .perform(get(ACCOUNT + "/value-points"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.points[0].time").value("2026-11-16T12:00:00Z"))
        .andExpect(jsonPath("$.points[0].value").value(10004.5));
  }
}
