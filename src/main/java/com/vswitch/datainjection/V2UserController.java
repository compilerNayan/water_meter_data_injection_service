package com.vswitch.datainjection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class V2UserController {

    private final UserService userService;

    V2UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/v2/users")
    ResponseEntity<UserResponse> registerUser(
            @AuthenticationPrincipal Jwt jwt, @RequestBody CreateUserRequest request) {
        String userId = jwt.getSubject();
        String tokenEmail = jwt.getClaimAsString("email");
        var result = userService.registerUser(userId, tokenEmail, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/v2/users/me")
    UserResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return userService.getMe(jwt.getSubject());
    }
}
