package com.vswitch.datainjection;

import java.util.List;

public record BlockDto(String id, String label, List<WingDto> wings) {}
