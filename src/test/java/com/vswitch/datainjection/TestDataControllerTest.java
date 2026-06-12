package com.vswitch.datainjection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestDataControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TestDataStore testDataStore;

    @Test
    void putStoresAndReturnsKeyValue() throws Exception {
        mockMvc.perform(put("/api/test-data/hello/world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("hello"))
                .andExpect(jsonPath("$.value").value("world"));

        verify(testDataStore).save("hello", "world");
    }

    @Test
    void getReturnsStoredValue() throws Exception {
        when(testDataStore.find("hello")).thenReturn(Optional.of("world"));

        mockMvc.perform(get("/api/test-data/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("hello"))
                .andExpect(jsonPath("$.value").value("world"));
    }

    @Test
    void getReturnsNotFoundWhenMissing() throws Exception {
        when(testDataStore.find("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/test-data/missing")).andExpect(status().isNotFound());
    }
}
