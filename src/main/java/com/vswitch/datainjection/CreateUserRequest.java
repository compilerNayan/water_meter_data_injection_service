package com.vswitch.datainjection;

public record CreateUserRequest(
        String email, String phone, String firstName, String lastName) {}
