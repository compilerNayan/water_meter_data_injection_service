package com.vswitch.datainjection;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TenantMetadataService {

    private final TenantService tenantService;
    private final UnitService unitService;
    private final UserService userService;
    private final TenantMetadataHasher hasher;

    TenantMetadataService(
            TenantService tenantService,
            UnitService unitService,
            UserService userService,
            ObjectMapper objectMapper) {
        this.tenantService = tenantService;
        this.unitService = unitService;
        this.userService = userService;
        this.hasher = new TenantMetadataHasher(objectMapper);
    }

    TenantMetadataHashResponse getHash(String tenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        if (tenant.metadataHash() != null && !tenant.metadataHash().isBlank()) {
            return new TenantMetadataHashResponse(tenant.metadataHash());
        }
        String hash = recomputeAndPersist(tenantId);
        return new TenantMetadataHashResponse(hash);
    }

    TenantMetadataResponse getMetadata(String tenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        Optional<UserRecord> owner = loadOwner(tenant);
        List<UnitRecord> units = unitService.listUnitRecords(tenantId);
        String hash = hasher.computeHash(tenant, owner, units);
        if (!hash.equals(tenant.metadataHash())) {
            tenantService.persistMetadataHash(tenantId, hash);
        }
        return buildResponse(tenant, owner, units, hash);
    }

    String recomputeAndPersist(String tenantId) {
        TenantRecord tenant = requireTenant(tenantId);
        Optional<UserRecord> owner = loadOwner(tenant);
        List<UnitRecord> units = unitService.listUnitRecords(tenantId);
        String hash = hasher.computeHash(tenant, owner, units);
        tenantService.persistMetadataHash(tenantId, hash);
        return hash;
    }

    private TenantMetadataResponse buildResponse(
            TenantRecord tenant,
            Optional<UserRecord> owner,
            List<UnitRecord> units,
            String hash) {
        TenantResponse tenantResponse = tenantService.toResponse(tenant);
        return new TenantMetadataResponse(
                hash,
                tenant.tenantId(),
                tenantResponse.name(),
                tenantResponse.structure(),
                owner.map(this::toOwnerEntry).orElse(null),
                units.stream().map(this::toDeviceEntry).toList());
    }

    private TenantMetadataOwnerEntry toOwnerEntry(UserRecord user) {
        return new TenantMetadataOwnerEntry(
                user.userId(),
                emptyToNull(user.displayName()),
                emptyToNull(user.email()),
                emptyToNull(user.phone()),
                emptyToNull(user.firstName()),
                emptyToNull(user.lastName()));
    }

    private TenantMetadataDeviceEntry toDeviceEntry(UnitRecord unit) {
        return new TenantMetadataDeviceEntry(
                unit.unitId(),
                unit.name(),
                unit.deviceId(),
                emptyToNull(unit.flatNumber()),
                emptyToNull(unit.floor()),
                emptyToNull(unit.block()),
                emptyToNull(unit.wing()),
                emptyToNull(unit.residentName()),
                emptyToNull(unit.phoneNumber()),
                emptyToNull(unit.notes()),
                unit.enrollmentStatus(),
                false,
                null,
                emptyToNull(unit.unitInviteCode()));
    }

    private Optional<UserRecord> loadOwner(TenantRecord tenant) {
        if (tenant.ownerUserId() == null || tenant.ownerUserId().isBlank()) {
            return Optional.empty();
        }
        return userService.findById(tenant.ownerUserId());
    }

    private TenantRecord requireTenant(String tenantId) {
        return tenantService
                .findById(tenantId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
