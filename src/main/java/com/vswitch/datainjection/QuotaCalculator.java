package com.vswitch.datainjection;

import java.util.Comparator;
import java.util.List;

public final class QuotaCalculator {

    private QuotaCalculator() {}

    public static QuotaCapResult computeCap(
            List<QuotaStepDto> steps, double usedLiters, double dailyLimitLiters) {
        List<QuotaStepDto> sorted =
                steps.stream()
                        .sorted(Comparator.comparingDouble(QuotaStepDto::atLitersUsed))
                        .toList();

        double cap = 100.0;
        int activeIndex = -1;
        Double nextStepAt = null;

        for (int i = 0; i < sorted.size(); i++) {
            QuotaStepDto step = sorted.get(i);
            if (usedLiters < step.atLitersUsed()) {
                nextStepAt = step.atLitersUsed();
                break;
            }
            activeIndex = i;
            if ("turn_off".equals(step.action())) {
                cap = 0;
            } else if ("reduce_pressure".equals(step.action()) && step.value() != null) {
                cap -= step.value();
            }
            if (i + 1 < sorted.size()) {
                nextStepAt = sorted.get(i + 1).atLitersUsed();
            } else {
                nextStepAt = null;
            }
        }

        cap = Math.max(0, Math.min(100, cap));
        double remaining =
                Math.max(0, Math.min(dailyLimitLiters, dailyLimitLiters - usedLiters));

        Double capPercent = activeIndex >= 0 && cap < 100 ? cap : null;
        if (activeIndex >= 0 && cap == 0) {
            capPercent = 0.0;
        }

        return new QuotaCapResult(capPercent, activeIndex, nextStepAt, remaining);
    }

    public record QuotaCapResult(
            Double capPercent, int activeStepIndex, Double nextStepAtLiters, double remainingLiters) {}
}
