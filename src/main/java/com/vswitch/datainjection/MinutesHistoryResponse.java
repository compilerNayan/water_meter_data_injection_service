package com.vswitch.datainjection;

import java.util.List;

public record MinutesHistoryResponse(
        String deviceId, String timezone, int slotMinutes, List<MinutesDayResponse> days) {}
