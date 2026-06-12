package com.vswitch.datainjection;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class UserService {

    private final DynamoDbClient dynamoDbClient;
    private final TenantService tenantService;
    private final String tableName;

    UserService(
            DynamoDbClient dynamoDbClient,
            TenantService tenantService,
            @Value("${users.table.name:WaterMeterUsers}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tenantService = tenantService;
        this.tableName = tableName;
    }

    public Optional<UserRecord> findById(String userId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "userId",
                                                AttributeValue.builder().s(userId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UserRecord.fromItem(response.item()));
    }

    UserResponse getMe(String userId) {
        return findById(userId)
                .map(UserRecord::toResponse)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "User not registered"));
    }

    UserRegistrationResult registerUser(
            String userId, String tokenEmail, CreateUserRequest request) {
        validateRequest(request);

        var existing = findById(userId);
        if (existing.isPresent()) {
            return new UserRegistrationResult(existing.get().toResponse(), false);
        }

        if (tokenEmail != null
                && !tokenEmail.isBlank()
                && request.email() != null
                && !request.email().equalsIgnoreCase(tokenEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Email does not match authenticated user");
        }

        TenantRecord tenant = tenantService.createTenantForOwner(userId);
        String now = Instant.now().toString();
        String displayName = buildDisplayName(request.firstName(), request.lastName());

        UserRecord user =
                new UserRecord(
                        userId,
                        request.email(),
                        nullToEmpty(request.phone()),
                        request.firstName(),
                        request.lastName(),
                        displayName,
                        tenant.tenantId(),
                        true,
                        false,
                        now,
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(user.toItem())
                        .build());

        return new UserRegistrationResult(user.toResponse(), true);
    }

    record UserRegistrationResult(UserResponse response, boolean created) {}

    void requireTenantMember(String userId, String tenantId) {
        UserRecord user = requireUser(userId);
        if (user.tenantId() == null || user.tenantId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User is not associated with a tenant");
        }
        if (!user.tenantId().equals(tenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tenant does not match authenticated user");
        }
    }

    void requireTenantOwner(String userId, String tenantId) {
        UserRecord user = requireUser(userId);
        requireTenantMember(userId, tenantId);
        if (!user.isTenantOwner()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the tenant owner can perform this action");
        }
    }

    void joinTenantAsCoAdmin(String userId, String tenantId) {
        UserRecord user = requireUser(userId);
        if (user.tenantId() != null && !user.tenantId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User already belongs to a tenant");
        }

        String now = Instant.now().toString();
        UserRecord updated =
                new UserRecord(
                        user.userId(),
                        user.email(),
                        user.phone(),
                        user.firstName(),
                        user.lastName(),
                        user.displayName(),
                        tenantId,
                        false,
                        true,
                        user.createdAt(),
                        now);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(updated.toItem())
                        .build());
    }

    void completeOnboarding(String userId) {
        UserRecord user = requireUser(userId);
        if (user.onboardingComplete()) {
            return;
        }
        String now = Instant.now().toString();
        UserRecord updated =
                new UserRecord(
                        user.userId(),
                        user.email(),
                        user.phone(),
                        user.firstName(),
                        user.lastName(),
                        user.displayName(),
                        user.tenantId(),
                        user.isTenantOwner(),
                        true,
                        user.createdAt(),
                        now);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(updated.toItem())
                        .build());
    }

    private UserRecord requireUser(String userId) {
        return findById(userId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "User not registered"));
    }

    private void validateRequest(CreateUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body required");
        }
        if (isBlank(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (isBlank(request.firstName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "firstName is required");
        }
        if (isBlank(request.lastName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lastName is required");
        }
    }

    private static String buildDisplayName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
