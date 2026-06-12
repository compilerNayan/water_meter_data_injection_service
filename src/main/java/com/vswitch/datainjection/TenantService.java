package com.vswitch.datainjection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

@Service
public class TenantService {

    private static final String DEFAULT_STRUCTURE = "{\"blocks\":[]}";
    private static final String BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int TENANT_ID_LENGTH = 7;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final TenantMetadataService tenantMetadataService;

    TenantService(
            DynamoDbClient dynamoDbClient,
            @Value("${tenants.table.name:WaterMeterTenants}") String tableName,
            ObjectMapper objectMapper,
            @Autowired @Lazy TenantMetadataService tenantMetadataService) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.objectMapper = objectMapper;
        this.tenantMetadataService = tenantMetadataService;
    }

    TenantRecord createTenantForOwner(String ownerUserId) {
        String now = Instant.now().toString();
        String tenantId = generateUniqueTenantId();
        TenantRecord tenant =
                new TenantRecord(
                        tenantId, "", ownerUserId, DEFAULT_STRUCTURE, now, now, null, null, null);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(tenant.toItem())
                        .build());
        return tenant;
    }

    String generateUniqueTenantId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = randomBase36(TENANT_ID_LENGTH);
            if (findById(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate tenant id");
    }

    private String randomBase36(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(BASE36.charAt(random.nextInt(BASE36.length())));
        }
        return builder.toString();
    }

    Optional<TenantRecord> findById(String tenantId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "tenantId",
                                                AttributeValue.builder().s(tenantId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TenantRecord.fromItem(response.item()));
    }

    TenantResponse getTenant(String tenantId) {
        TenantRecord tenant =
                findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Tenant not found"));
        return toResponse(tenant);
    }

    TenantResponse updateBuilding(String tenantId, String name, StructureDto structure) {
        validateBuildingName(name);
        validateStructure(structure);
        String structureJson = serializeStructure(structure);

        TenantRecord existing =
                findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Tenant not found"));

        String now = Instant.now().toString();
        TenantRecord updated =
                copyWithStructure(existing, name.trim(), structureJson, now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(updated.toItem())
                        .build());

        tenantMetadataService.recomputeAndPersist(tenantId);
        return toResponse(updated);
    }

    TenantResponse updateStructure(String tenantId, StructureDto structure) {
        validateStructure(structure);
        String structureJson = serializeStructure(structure);

        TenantRecord existing =
                findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Tenant not found"));

        String now = Instant.now().toString();
        TenantRecord updated = copyWithStructure(existing, existing.name(), structureJson, now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(updated.toItem())
                        .build());

        tenantMetadataService.recomputeAndPersist(tenantId);
        return toResponse(updated);
    }

    AdminInviteResponse setAdminInvite(String tenantId, String inviteCode, String expiresAt) {
        TenantRecord existing =
                findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Tenant not found"));
        String now = Instant.now().toString();
        TenantRecord updated =
                new TenantRecord(
                        tenantId,
                        existing.name(),
                        existing.ownerUserId(),
                        existing.structure(),
                        existing.createdAt(),
                        now,
                        existing.metadataHash(),
                        inviteCode,
                        expiresAt);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(updated.toItem())
                        .build());
        return new AdminInviteResponse(inviteCode, expiresAt);
    }

    Optional<TenantRecord> findByAdminInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = inviteCode.trim().toUpperCase();
        var response =
                dynamoDbClient.scan(
                        ScanRequest.builder()
                                .tableName(tableName)
                                .filterExpression("adminInviteCode = :code")
                                .expressionAttributeValues(
                                        Map.of(
                                                ":code",
                                                AttributeValue.builder().s(normalized).build()))
                                .build());
        if (response.items() == null || response.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TenantRecord.fromItem(response.items().get(0)));
    }

    void persistMetadataHash(String tenantId, String metadataHash) {
        TenantRecord existing =
                findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantRecord updated = existing.withMetadataHash(metadataHash);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(updated.toItem())
                        .build());
    }

    TenantResponse toResponse(TenantRecord tenant) {
        return new TenantResponse(
                tenant.tenantId(), tenant.name(), deserializeStructure(tenant.structure()));
    }

    void validateBuildingName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Building name is required");
        }
    }

    void validateStructure(StructureDto structure) {
        if (structure == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "structure is required");
        }

        List<BlockDto> blocks = structure.blocks() == null ? List.of() : structure.blocks();
        Set<String> blockIds = new HashSet<>();

        for (BlockDto block : blocks) {
            if (block == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Block entries cannot be null");
            }
            if (block.id() == null || block.id().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Each block requires an id");
            }
            String blockId = block.id().trim();
            if (!blockIds.add(blockId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Duplicate block id: " + blockId);
            }

            List<WingDto> wings = block.wings() == null ? List.of() : block.wings();
            Set<String> wingNames = new HashSet<>();
            for (WingDto wing : wings) {
                if (wing == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Wing entries cannot be null");
                }
                if (wing.name() == null || wing.name().isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Each wing requires a name in block " + blockId);
                }
                String wingName = wing.name().trim();
                if (!wingNames.add(wingName)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Duplicate wing name in block " + blockId + ": " + wingName);
                }
                int floorCount = wing.floorCount() == null ? 0 : wing.floorCount();
                if (floorCount < 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "floorCount must be >= 0 for wing " + wingName);
                }
            }
        }
    }

    String serializeStructure(StructureDto structure) {
        try {
            return objectMapper.writeValueAsString(structure);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize structure");
        }
    }

    StructureDto deserializeStructure(String structureJson) {
        if (structureJson == null || structureJson.isBlank()) {
            return new StructureDto(List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(structureJson);
            JsonNode blocksNode = root.get("blocks");
            if (blocksNode == null || !blocksNode.isArray()) {
                return new StructureDto(List.of());
            }

            List<BlockDto> blocks = new ArrayList<>();
            for (JsonNode blockNode : blocksNode) {
                String id = textOrEmpty(blockNode, "id");
                String label = blockNode.hasNonNull("label") ? blockNode.get("label").asText() : id;
                List<WingDto> wings = parseWings(blockNode.get("wings"));
                blocks.add(new BlockDto(id, label, wings));
            }
            return new StructureDto(blocks);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return new StructureDto(List.of());
        }
    }

    private List<WingDto> parseWings(JsonNode wingsNode) {
        if (wingsNode == null || !wingsNode.isArray()) {
            return List.of();
        }
        List<WingDto> wings = new ArrayList<>();
        for (JsonNode wingNode : wingsNode) {
            if (!wingNode.isObject()) {
                continue;
            }
            String name = textOrEmpty(wingNode, "name");
            int floorCount =
                    wingNode.has("floorCount") ? wingNode.get("floorCount").asInt(0) : 0;
            wings.add(new WingDto(name, floorCount));
        }
        return wings;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return node.get(field).asText();
    }

    private static TenantRecord copyWithStructure(
            TenantRecord existing, String name, String structureJson, String updatedAt) {
        return new TenantRecord(
                existing.tenantId(),
                name,
                existing.ownerUserId(),
                structureJson,
                existing.createdAt(),
                updatedAt,
                existing.metadataHash(),
                existing.adminInviteCode(),
                existing.adminInviteExpiresAt());
    }
}
