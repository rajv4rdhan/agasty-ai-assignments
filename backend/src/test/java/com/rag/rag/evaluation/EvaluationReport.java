package com.rag.rag.evaluation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Summary report of evaluation results.
 */
public class EvaluationReport {
    
    private final List<EvaluationResult> results;
    private final long totalTests;
    private final long passedTests;
    private final long failedTests;
    private final double passRate;
    
    // Category-specific metrics
    private final Map<String, Long> passedByCategory;
    private final Map<String, Long> totalByCategory;
    
    // Feature-specific metrics
    private final long retrievalSuccessCount;
    private final long refusalCorrectnessCount;
    private final long citationCorrectnessCount;
    private final long tenantIsolationCount;
    private final long answerQualityCount;
    
    public EvaluationReport(List<EvaluationResult> results) {
        this.results = results;
        this.totalTests = results.size();
        this.passedTests = results.stream().filter(EvaluationResult::isPassed).count();
        this.failedTests = totalTests - passedTests;
        this.passRate = totalTests > 0 ? (double) passedTests / totalTests * 100 : 0;
        
        // Calculate category metrics
        this.passedByCategory = results.stream()
            .filter(EvaluationResult::isPassed)
            .collect(Collectors.groupingBy(EvaluationResult::getCategory, Collectors.counting()));
        
        this.totalByCategory = results.stream()
            .collect(Collectors.groupingBy(EvaluationResult::getCategory, Collectors.counting()));
        
        // Calculate feature metrics
        this.retrievalSuccessCount = results.stream().filter(EvaluationResult::isRetrievalSuccess).count();
        this.refusalCorrectnessCount = results.stream().filter(EvaluationResult::isRefusalCorrectness).count();
        this.citationCorrectnessCount = results.stream().filter(EvaluationResult::isCitationCorrectness).count();
        this.tenantIsolationCount = results.stream().filter(EvaluationResult::isTenantIsolation).count();
        this.answerQualityCount = results.stream().filter(EvaluationResult::isAnswerQuality).count();
    }
    
    /**
     * Generate a formatted report string.
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("═══════════════════════════════════════════════════════════\n");
        report.append("              RAG EVALUATION REPORT\n");
        report.append("═══════════════════════════════════════════════════════════\n\n");
        
        // Overall metrics
        report.append("OVERALL RESULTS:\n");
        report.append("─────────────────────────────────────────────────────────\n");
        report.append(String.format("Total Tests:     %d\n", totalTests));
        report.append(String.format("Passed:          %d\n", passedTests));
        report.append(String.format("Failed:          %d\n", failedTests));
        report.append(String.format("Pass Rate:       %.1f%%\n\n", passRate));
        
        // Feature-specific metrics
        report.append("FEATURE METRICS:\n");
        report.append("─────────────────────────────────────────────────────────\n");
        report.append(String.format("Retrieval Success:      %d/%d (%.1f%%)\n", 
            retrievalSuccessCount, totalTests, (double) retrievalSuccessCount / totalTests * 100));
        report.append(String.format("Refusal Correctness:    %d/%d (%.1f%%)\n", 
            refusalCorrectnessCount, totalTests, (double) refusalCorrectnessCount / totalTests * 100));
        report.append(String.format("Citation Correctness:   %d/%d (%.1f%%)\n", 
            citationCorrectnessCount, totalTests, (double) citationCorrectnessCount / totalTests * 100));
        report.append(String.format("Tenant Isolation:       %d/%d (%.1f%%)\n", 
            tenantIsolationCount, totalTests, (double) tenantIsolationCount / totalTests * 100));
        report.append(String.format("Answer Quality:         %d/%d (%.1f%%)\n\n", 
            answerQualityCount, totalTests, (double) answerQualityCount / totalTests * 100));
        
        // Category breakdown
        report.append("CATEGORY BREAKDOWN:\n");
        report.append("─────────────────────────────────────────────────────────\n");
        for (Map.Entry<String, Long> entry : totalByCategory.entrySet()) {
            String category = entry.getKey();
            long total = entry.getValue();
            long passed = passedByCategory.getOrDefault(category, 0L);
            double categoryPassRate = total > 0 ? (double) passed / total * 100 : 0;
            report.append(String.format("%-25s %d/%d (%.1f%%)\n", 
                category + ":", passed, total, categoryPassRate));
        }
        report.append("\n");
        
        // Failed tests details
        List<EvaluationResult> failedResults = results.stream()
            .filter(r -> !r.isPassed())
            .collect(Collectors.toList());
        
        if (!failedResults.isEmpty()) {
            report.append("FAILED TESTS:\n");
            report.append("─────────────────────────────────────────────────────────\n");
            for (EvaluationResult result : failedResults) {
                report.append(String.format("❌ %s (%s)\n", result.getCaseId(), result.getCategory()));
                for (String failure : result.getFailures()) {
                    report.append(String.format("   - %s\n", failure));
                }
                report.append(String.format("   Actual: %s\n", 
                    result.isActualRefused() ? "REFUSED" : "ANSWERED"));
                if (!result.getActualSources().isEmpty()) {
                    report.append(String.format("   Sources: %s\n", String.join(", ", result.getActualSources())));
                }
                report.append("\n");
            }
        }
        
        // Passed tests summary
        List<EvaluationResult> passedResults = results.stream()
            .filter(EvaluationResult::isPassed)
            .collect(Collectors.toList());
        
        if (!passedResults.isEmpty()) {
            report.append("PASSED TESTS:\n");
            report.append("─────────────────────────────────────────────────────────\n");
            for (EvaluationResult result : passedResults) {
                report.append(String.format("✓ %s (%s)\n", result.getCaseId(), result.getCategory()));
            }
        }
        
        report.append("\n═══════════════════════════════════════════════════════════\n");
        
        return report.toString();
    }
    
    public List<EvaluationResult> getResults() {
        return results;
    }
    
    public long getTotalTests() {
        return totalTests;
    }
    
    public long getPassedTests() {
        return passedTests;
    }
    
    public long getFailedTests() {
        return failedTests;
    }
    
    public double getPassRate() {
        return passRate;
    }
}
