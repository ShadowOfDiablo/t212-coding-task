package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.account.AccountResetService;
import com.tihomir.t212.tradingbot.api.response.AccountResetListResponse;
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

  public AccountController(AccountResetService accountResetService) {
    this.accountResetService = accountResetService;
  }

  @PostMapping("/reset")
  public EmptiedAccountResponse emptyAccount(@PathVariable String broker) {
    return EmptiedAccountResponse.of(accountResetService.emptyAccount(broker));
  }

  @GetMapping("/resets")
  public AccountResetListResponse getAccountResets(@PathVariable String broker) {
    return AccountResetListResponse.of(accountResetService.getAccountResets(broker));
  }
}
