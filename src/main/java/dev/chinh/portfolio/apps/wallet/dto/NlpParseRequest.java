package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NlpParseRequest(
    @NotBlank(message = "text must not be blank")
    @Size(max = 200, message = "text must not exceed 200 characters")
    String text
) {}
