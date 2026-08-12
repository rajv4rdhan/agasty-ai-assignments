package com.rag.rag.integration;

import com.rag.rag.AbstractIntegrationTest;
import com.rag.rag.evaluation.EvaluationCase;
import com.rag.rag.evaluation.EvaluationHarness;
import com.rag.rag.evaluation.EvaluationReport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the evaluation harness.
 * This test demonstrates the evaluation framework in action.
 */
class EvaluationHarnessIntegrationTest extends AbstractIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(EvaluationHarnessIntegrationTest.class);
    
    @Autowired
    private EvaluationHarness evaluationHarness;
    
    @Test
    void shouldLoadAndRunEvaluationCases() throws Exception {
        // Load evaluation cases
        List<EvaluationCase> cases = evaluationHarness.loadCases();
        
        // Verify cases loaded
        assertThat(cases).isNotEmpty();
        log.info("Loaded {} evaluation cases", cases.size());
        
        // Run evaluation
        EvaluationReport report = evaluationHarness.runEvaluation(cases);
        
        // Generate and print report
        String reportText = report.generateReport();
        System.out.println(reportText);
        log.info("Evaluation complete. Pass rate: {}", report.getPassRate());
        
        // Verify we got results
        assertThat(report.getTotalTests()).isGreaterThan(0);
        assertThat(report.getResults()).hasSize(cases.size());
    }
}
