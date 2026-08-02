package com.exploreceylon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void allowsProductionFrontendOrigin5173() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://3.109.16.23:5173")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://3.109.16.23:5173"));
    }

    @Test
    void allowsProductionFrontendOrigin5174() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://3.109.16.23:5174")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://3.109.16.23:5174"));
    }

    @Test
    void allowsLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
