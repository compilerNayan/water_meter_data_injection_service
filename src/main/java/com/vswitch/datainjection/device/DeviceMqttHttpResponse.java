package com.vswitch.datainjection.device;

import java.util.Map;

public record DeviceMqttHttpResponse(int statusCode, Map<String, Object> body) {}
