package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.account.AccountResetService;
import com.tihomir.t212.tradingbot.account.value.AccountValueService;
import com.tihomir.t212.tradingbot.api.response.AccountResetListResponse;
import com.tihomir.t212.tradingbot.api.response.AccountValuePointListResponse;
import com.tihomir.t212.tradingbot.api.response.EmptiedAccountResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}/account")
public class AccountController {

  private final AccountResetService accountResetService;
  private final AccountValueService accountValueService;

  public AccountController(
      AccountResetService accountResetService, AccountValueService accountValueService) {
    this.accountResetService = accountResetService;
    this.accountValueService = accountValueService;
  }

  @PostMapping("/reset")
  public EmptiedAccountResponse emptyAccount(@PathVariable String broker) {
    return EmptiedAccountResponse.of(accountResetService.emptyAccount(broker));
  }

  /** The whole account value, sampled every minute since the app started. */
  @GetMapping("/value-points")
  public AccountValuePointListResponse getAccountValuePoints(@PathVariable String broker) {
    return AccountValuePointListResponse.of(accountValueService.getAccountValuePoints(broker));
  }

  @GetMapping("/resets")
  public AccountResetListResponse getAccountResets(@PathVariable String broker) {
    return AccountResetListResponse.of(accountResetService.getAccountResets(broker));
  }
}
