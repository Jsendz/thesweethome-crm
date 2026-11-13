package com.sweethomecompany.crm.auth;

import com.sweethomecompany.crm.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new CurrentUserResponse(principal.getId(), principal.getUsername(), principal.getFullName(), principal.getRole());
    }
}
