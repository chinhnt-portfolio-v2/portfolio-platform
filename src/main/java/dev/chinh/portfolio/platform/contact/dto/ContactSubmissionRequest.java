package dev.chinh.portfolio.platform.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactSubmissionRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 5000, message = "Message must be between 10 and 5000 characters")
        String message,

        String referralSource
) {}
