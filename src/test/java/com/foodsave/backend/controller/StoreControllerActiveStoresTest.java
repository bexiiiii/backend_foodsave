package com.foodsave.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the StoreController /api/stores/active endpoint
 * Tests the fix for the 500 Internal Server Error issue
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
public class StoreControllerActiveStoresTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Test that the /api/stores/active endpoint returns 200 OK with empty array when no stores exist
     * This test validates the fix for the 500 Internal Server Error
     */
    @Test
    public void testGetActiveStores_EmptyDatabase_Returns200WithEmptyArray() {
        // When: GET /api/stores/active with empty database
        ResponseEntity<String> response = restTemplate.getForEntity("/api/stores/active", String.class);

        // Then: Should return 200 OK with empty JSON array
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    /**
     * Test that the endpoint handles errors gracefully without throwing 500 errors
     */
    @Test
    public void testGetActiveStores_GracefulErrorHandling_Returns200() {
        // When: GET /api/stores/active (even with potential database issues)
        ResponseEntity<String> response = restTemplate.getForEntity("/api/stores/active", String.class);

        // Then: Should return 200 OK (not 500 Internal Server Error)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // The content should be a valid JSON array (might be empty)
        String responseContent = response.getBody();
        assertNotNull(responseContent);
        assertTrue(responseContent.startsWith("[") && responseContent.endsWith("]"), 
                  "Response should be a valid JSON array");
    }
}