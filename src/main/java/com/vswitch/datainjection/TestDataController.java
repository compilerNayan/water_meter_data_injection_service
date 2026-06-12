package com.vswitch.datainjection;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/test-data")
public class TestDataController {

    private final TestDataStore testDataStore;

    TestDataController(TestDataStore testDataStore) {
        this.testDataStore = testDataStore;
    }

    @PutMapping("/{key}/{value}")
    public TestDataResponse save(@PathVariable String key, @PathVariable String value) {
        testDataStore.save(key, value);
        return new TestDataResponse(key, value);
    }

    @GetMapping("/{key}")
    public TestDataResponse get(@PathVariable String key) {
        return testDataStore
                .find(key)
                .map(value -> new TestDataResponse(key, value))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "No data for key: " + key));
    }
}
