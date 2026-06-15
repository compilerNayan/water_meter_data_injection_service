package com.vswitch.datainjection;

import java.util.List;

public record BulkDummyEnrollResponse(
        String tenantId,
        int requested,
        int enrolled,
        int failed,
        List<BulkDummyEnrollItemResult> results) {}
