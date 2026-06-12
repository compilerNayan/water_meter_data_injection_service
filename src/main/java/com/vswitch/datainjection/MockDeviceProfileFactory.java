package com.vswitch.datainjection;

import org.springframework.stereotype.Component;

@Component
public class MockDeviceProfileFactory {

    MockDeviceProfile forDevice(String deviceId) {
        int seed = Math.abs(deviceId.hashCode());
        double dailyTarget = 600 + (seed % 601);
        MockDeviceProfile.AnomalyType anomaly = MockDeviceProfile.AnomalyType.NORMAL;
        int mod = seed % 100;
        if (mod < 8) {
            anomaly = MockDeviceProfile.AnomalyType.LEAK_BURST;
        } else if (mod < 12) {
            anomaly = MockDeviceProfile.AnomalyType.VALVE_MISMATCH;
        } else if (mod < 18) {
            anomaly = MockDeviceProfile.AnomalyType.OFFLINE;
        }
        return new MockDeviceProfile(deviceId, dailyTarget, anomaly, seed);
    }

    double hourlyPatternLiters(double hour) {
        double morning = Math.exp(-Math.pow(hour - 7, 2) / 8) * 8;
        double evening = Math.exp(-Math.pow(hour - 19, 2) / 10) * 10;
        return 0.8 + morning + evening;
    }

    double minuteVolumeLiters(MockDeviceProfile profile, java.time.ZonedDateTime time) {
        double hour = time.getHour() + time.getMinute() / 60.0;
        double weekendFactor =
                time.getDayOfWeek().getValue() >= 6 ? 1.1 : 1.0;
        double hourlyLiters = hourlyPatternLiters(hour) * weekendFactor;
        double baseVolume = (profile.dailyTargetLiters() / 1440.0) * (hourlyLiters / 3.0);
        double noise = 0.85 + pseudoRandom(profile.seed(), time) * 0.3;
        return Math.max(0, baseVolume * noise);
    }

    boolean isOfflineWindow(MockDeviceProfile profile, java.time.Instant instant) {
        if (profile.anomalyType() != MockDeviceProfile.AnomalyType.OFFLINE) {
            return false;
        }
        long epochMinute = instant.getEpochSecond() / 60;
        return (epochMinute + profile.seed()) % 180 < 30;
    }

    boolean isLeakBurstMinute(MockDeviceProfile profile, java.time.ZonedDateTime time) {
        if (profile.anomalyType() != MockDeviceProfile.AnomalyType.LEAK_BURST) {
            return false;
        }
        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        int burstStart = profile.seed() % 1200;
        return minuteOfDay >= burstStart && minuteOfDay < burstStart + 4;
    }

    boolean isValveMismatchMinute(MockDeviceProfile profile, java.time.ZonedDateTime time) {
        return profile.anomalyType() == MockDeviceProfile.AnomalyType.VALVE_MISMATCH
                && time.getHour() == 3;
    }

    private static double pseudoRandom(int seed, java.time.ZonedDateTime time) {
        int mixed = seed ^ (time.getDayOfYear() * 10000 + time.getHour() * 60 + time.getMinute());
        return (mixed & 0xFFFF) / 65535.0;
    }
}
