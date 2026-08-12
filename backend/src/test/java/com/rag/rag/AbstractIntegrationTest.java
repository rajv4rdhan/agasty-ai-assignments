package com.rag.rag;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using Testcontainers.
 * Provides a PostgreSQL container with pgvector extension enabled.
 * Automatically runs Flyway migrations on startup and cleans up data after each test.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {
    
    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("rag_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-init.sql");
    
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Disable actual LLM calls in tests
        registry.add("spring.ai.vertex.ai.gemini.project-id", () -> "test-project");
        registry.add("spring.ai.vertex.ai.gemini.location", () -> "us-central1");
    }
    
    @AfterEach
    void cleanup() {
        // Clean up test data in reverse order of dependencies
        jdbcTemplate.execute("DELETE FROM message_citations");
        jdbcTemplate.execute("DELETE FROM messages");
        jdbcTemplate.execute("DELETE FROM conversations");
        jdbcTemplate.execute("DELETE FROM document_chunks");
        jdbcTemplate.execute("DELETE FROM documents");
    }
}
