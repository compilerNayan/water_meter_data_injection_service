package com.vswitch.datainjection;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminInviteService {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_SUFFIX_LENGTH = 4;

    private final TenantService tenantService;
    private final UserService userService;
    private final SecureRandom random = new SecureRandom();

    AdminInviteService(TenantService tenantService, UserService userService) {
        this.tenantService = tenantService;
        this.userService = userService;
    }

    AdminInviteResponse createAdminInvite(String userId, String tenantId) {
        userService.requireTenantOwner(userId, tenantId);
        String expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        String inviteCode = generateInviteCode();
        return tenantService.setAdminInvite(tenantId, inviteCode, expiresAt);
    }

    JoinAdminResponse joinAsAdmin(String userId, JoinAdminRequest request) {
        if (request == null || request.inviteCode() == null || request.inviteCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inviteCode is required");
        }

        TenantRecord tenant =
                tenantService
                        .findByAdminInviteCode(request.inviteCode())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Invalid admin invite code"));

        if (tenant.adminInviteExpiresAt() != null && !tenant.adminInviteExpiresAt().isBlank()) {
            Instant expires = Instant.parse(tenant.adminInviteExpiresAt());
            if (expires.isBefore(Instant.now())) {
                throw new ResponseStatusException(
                        HttpStatus.GONE, "Admin invite code has expired");
            }
        }

        userService.joinTenantAsCoAdmin(userId, tenant.tenantId());
        return new JoinAdminResponse(tenant.tenantId(), true, false);
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder("ADMIN-");
        for (int i = 0; i < INVITE_SUFFIX_LENGTH; i++) {
            builder.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return builder.toString();
    }
}
