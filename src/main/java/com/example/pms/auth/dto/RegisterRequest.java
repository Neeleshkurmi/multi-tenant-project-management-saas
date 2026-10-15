
package com.example.pms.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128,
                message = "Password must contain between 12 and 128 characters")
        String password,

        @NotBlank(message = "Organization name is required")
        @Size(max = 120,
                message = "Organization name must not exceed 120 characters")
        String organizationName

) {
}
