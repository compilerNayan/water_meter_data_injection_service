package com.vswitch.datainjection.device;

import java.time.Instant;

public record MinuteBucketEntry(Instant t, double ml) {}
