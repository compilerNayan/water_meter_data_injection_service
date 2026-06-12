package com.vswitch.datainjection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class QuotaStepsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuotaStepsJson() {}

    public static String toJson(List<QuotaStepDto> steps) {
        try {
            List<QuotaStepDto> sorted = sortSteps(steps);
            return MAPPER.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid quota steps", e);
        }
    }

    public static List<QuotaStepDto> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<QuotaStepDto> steps =
                    MAPPER.readValue(json, new TypeReference<List<QuotaStepDto>>() {});
            return sortSteps(steps);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public static List<QuotaStepDto> sortSteps(List<QuotaStepDto> steps) {
        List<QuotaStepDto> sorted = new ArrayList<>(steps);
        sorted.sort(Comparator.comparingDouble(QuotaStepDto::atLitersUsed));
        return sorted;
    }
}
