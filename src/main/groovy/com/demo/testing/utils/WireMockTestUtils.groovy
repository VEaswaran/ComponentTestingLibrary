package com.demo.testing.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.github.tomakehurst.wiremock.client.WireMock.*

/**
 * Utility class for WireMock testing operations.
 * Provides methods to stub HTTP endpoints, verify calls, and manage mock server interactions.
 */
class WireMockTestUtils {

    private static final Logger logger = LoggerFactory.getLogger(WireMockTestUtils.class)

    private final String wireMockUrl
    private final int port

    WireMockTestUtils(String wireMockUrl, int port) {
        this.wireMockUrl = wireMockUrl
        this.port = port
    }

    /**
     * Stub a GET endpoint with a response.
     *
     * @param path URL path to stub (e.g., "/api/users")
     * @param responseBody JSON response body
     * @param statusCode HTTP status code (default 200)
     */
    void stubGetEndpoint(String path, String responseBody, int statusCode = 200) {
        try {
            stubFor(get(urlEqualTo(path))
                    .willReturn(aResponse()
                            .withStatus(statusCode)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseBody)))
            logger.info("✅ Stubbed GET {} with status {}", path, statusCode)
        } catch (Exception e) {
            logger.error("❌ Failed to stub GET endpoint: {}", path, e)
            throw new RuntimeException("Failed to stub GET endpoint", e)
        }
    }

    /**
     * Stub a POST endpoint with a response.
     *
     * @param path URL path to stub
     * @param responseBody JSON response body
     * @param statusCode HTTP status code (default 200)
     */
    void stubPostEndpoint(String path, String responseBody, int statusCode = 200) {
        try {
            stubFor(post(urlEqualTo(path))
                    .willReturn(aResponse()
                            .withStatus(statusCode)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseBody)))
            logger.info("✅ Stubbed POST {} with status {}", path, statusCode)
        } catch (Exception e) {
            logger.error("❌ Failed to stub POST endpoint: {}", path, e)
            throw new RuntimeException("Failed to stub POST endpoint", e)
        }
    }

    /**
     * Stub a PUT endpoint with a response.
     *
     * @param path URL path to stub
     * @param responseBody JSON response body
     * @param statusCode HTTP status code (default 200)
     */
    void stubPutEndpoint(String path, String responseBody, int statusCode = 200) {
        try {
            stubFor(put(urlEqualTo(path))
                    .willReturn(aResponse()
                            .withStatus(statusCode)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseBody)))
            logger.info("✅ Stubbed PUT {} with status {}", path, statusCode)
        } catch (Exception e) {
            logger.error("❌ Failed to stub PUT endpoint: {}", path, e)
            throw new RuntimeException("Failed to stub PUT endpoint", e)
        }
    }

    /**
     * Stub a DELETE endpoint with a response.
     *
     * @param path URL path to stub
     * @param statusCode HTTP status code (default 204)
     */
    void stubDeleteEndpoint(String path, int statusCode = 204) {
        try {
            stubFor(delete(urlEqualTo(path))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))
            logger.info("✅ Stubbed DELETE {} with status {}", path, statusCode)
        } catch (Exception e) {
            logger.error("❌ Failed to stub DELETE endpoint: {}", path, e)
            throw new RuntimeException("Failed to stub DELETE endpoint", e)
        }
    }

    /**
     * Stub an endpoint with request body matching.
     *
     * @param method HTTP method (GET, POST, PUT, DELETE)
     * @param path URL path
     * @param requestBody Expected request body
     * @param responseBody Response body
     * @param statusCode HTTP status code
     */
    void stubEndpointWithBody(String method, String path, String requestBody, String responseBody, int statusCode = 200) {
        try {
            def request = null
            switch (method.toUpperCase()) {
                case "POST":
                    request = post(urlEqualTo(path))
                    break
                case "PUT":
                    request = put(urlEqualTo(path))
                    break
                default:
                    throw new IllegalArgumentException("Method ${method} not supported for body matching")
            }

            stubFor(request
                    .withRequestBody(equalToJson(requestBody))
                    .willReturn(aResponse()
                            .withStatus(statusCode)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseBody)))
            logger.info("✅ Stubbed {} {} with body matching", method, path)
        } catch (Exception e) {
            logger.error("❌ Failed to stub endpoint with body: {} {}", method, path, e)
            throw new RuntimeException("Failed to stub endpoint with body", e)
        }
    }

    /**
     * Verify that an endpoint was called.
     *
     * @param method HTTP method
     * @param path URL path
     * @param count Expected number of calls
     */
    void verifyEndpointCalled(String method, String path, int count = 1) {
        try {
            def requestPattern = null
            switch (method.toUpperCase()) {
                case "GET":
                    requestPattern = getRequestedFor(urlEqualTo(path))
                    break
                case "POST":
                    requestPattern = postRequestedFor(urlEqualTo(path))
                    break
                case "PUT":
                    requestPattern = putRequestedFor(urlEqualTo(path))
                    break
                case "DELETE":
                    requestPattern = deleteRequestedFor(urlEqualTo(path))
                    break
                default:
                    throw new IllegalArgumentException("Method ${method} not supported")
            }

            verify(count, requestPattern)
            logger.info("✅ Verified {} {} was called {} time(s)", method, path, count)
        } catch (Exception e) {
            logger.error("❌ Failed to verify endpoint call: {} {}", method, path, e)
            throw new RuntimeException("Failed to verify endpoint call", e)
        }
    }

    /**
     * Get the full URL for accessing the stubbed endpoint.
     *
     * @param path URL path (e.g., "/api/users")
     * @return Full URL
     */
    String getEndpointUrl(String path) {
        return "${wireMockUrl}:${port}${path}"
    }

    /**
     * Reset all stubs and calls.
     */
    void reset() {
        try {
            resetAllRequests()
            resetAllMappings()
            logger.info("✅ WireMock stubs and calls reset")
        } catch (Exception e) {
            logger.warn("⚠️ Warning resetting WireMock", e)
        }
    }
}

