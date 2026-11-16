package com.tihomir.t212.tradingbot.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FollowTickerRequestBody(
    @NotBlank
        @Pattern(regexp = "[A-Z0-9]{2,20}", message = "must be 2 to 20 capital letters or digits")
        String ticker) {}
