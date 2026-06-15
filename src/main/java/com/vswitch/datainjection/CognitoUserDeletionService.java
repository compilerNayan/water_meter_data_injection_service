package com.vswitch.datainjection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

@Service
@ConditionalOnProperty(name = "cognito.user.deletion.enabled", havingValue = "true", matchIfMissing = true)
public class CognitoUserDeletionService {

    private static final Logger log = LoggerFactory.getLogger(CognitoUserDeletionService.class);

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    CognitoUserDeletionService(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${cognito.user.pool.id}") String userPoolId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
    }

    boolean deleteUser(UserRecord user) {
        Optional<String> username = resolveUsername(user);
        if (username.isEmpty()) {
            log.warn("Could not resolve Cognito username for user {}", user.userId());
            return false;
        }
        try {
            cognitoClient.adminDeleteUser(
                    AdminDeleteUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(username.get())
                            .build());
            log.info("Deleted Cognito user {} ({})", username.get(), user.email());
            return true;
        } catch (CognitoIdentityProviderException e) {
            log.warn(
                    "Failed to delete Cognito user {} ({}): {}",
                    username.get(),
                    user.email(),
                    e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private Optional<String> resolveUsername(UserRecord user) {
        List<String> candidates = new ArrayList<>();
        if (user.userId() != null && !user.userId().isBlank()) {
            candidates.add(user.userId().trim());
        }
        if (user.email() != null && !user.email().isBlank()) {
            candidates.add(user.email().trim());
        }

        for (String candidate : candidates) {
            if (userExists(candidate)) {
                return Optional.of(candidate);
            }
        }

        if (user.email() != null && !user.email().isBlank()) {
            return findUsernameByEmail(user.email().trim());
        }
        return Optional.empty();
    }

    private boolean userExists(String username) {
        try {
            cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(username)
                            .build());
            return true;
        } catch (CognitoIdentityProviderException e) {
            if ("UserNotFoundException".equals(e.awsErrorDetails().errorCode())) {
                return false;
            }
            log.debug("Cognito lookup failed for {}: {}", username, e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private Optional<String> findUsernameByEmail(String email) {
        String escaped = email.replace("\"", "\\\"");
        var response =
                cognitoClient.listUsers(
                        ListUsersRequest.builder()
                                .userPoolId(userPoolId)
                                .filter("email = \"" + escaped + "\"")
                                .limit(1)
                                .build());
        for (UserType cognitoUser : response.users()) {
            if (cognitoUser.username() != null && !cognitoUser.username().isBlank()) {
                return Optional.of(cognitoUser.username());
            }
        }
        return Optional.empty();
    }
}
