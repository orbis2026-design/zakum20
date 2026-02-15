package net.orbis.zakum.core.platform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated platform infrastructure verification report.
 * Runs all validators and generates a comprehensive readiness assessment.
 */
public final class PlatformInfrastructureReport {

  private final File projectRoot;
  private final List<String> featureModules;
  private final List<String> allModules;

  public PlatformInfrastructureReport(File projectRoot, 
                                      List<String> featureModules,
                                      List<String> allModules) {
    this.projectRoot = projectRoot;
    this.featureModules = new ArrayList<>(featureModules);
    this.allModules = new ArrayList<>(allModules);
  }

  /**
   * Runs all platform verification checks and generates comprehensive report.
   */
  public String generateFullReport() {
    StringBuilder report = new StringBuilder();
    
    report.append("╔═══════════════════════════════════════════════════════════╗\n");
    report.append("║   ZAKUM20 PLATFORM INFRASTRUCTURE VERIFICATION REPORT    ║\n");
    report.append("║   PaperSpigot 1.21.1 + Java 21 + Folia Compatibility    ║\n");
    report.append("╚═══════════════════════════════════════════════════════════╝\n\n");

    // Run all validators
    ApiBoundaryValidator apiBoundary = new ApiBoundaryValidator(projectRoot, featureModules);
    AsyncSafetyChecker asyncSafety = new AsyncSafetyChecker(projectRoot, allModules);
    ConfigSnapshotGenerator configValidator = new ConfigSnapshotGenerator(projectRoot, allModules);
    ServiceResolutionValidator serviceValidator = new ServiceResolutionValidator(projectRoot, allModules);
    FoliaCompatibilityValidator foliaValidator = new FoliaCompatibilityValidator(projectRoot, allModules);
    DataHealthProbe dataProbe = new DataHealthProbe(projectRoot, allModules);

    ValidationResult[] results = {
        apiBoundary.validate(),
        asyncSafety.validate(),
        configValidator.validate(),
        serviceValidator.validate(),
        foliaValidator.validate(),
        dataProbe.validate()
    };

    // Executive Summary
    report.append("═══ EXECUTIVE SUMMARY ═══\n\n");
    
    int passed = 0;
    int total = results.length;
    
    for (ValidationResult result : results) {
      String status = result.isPassed() ? "✓ PASS" : "✗ FAIL";
      int violations = result.getViolations().size();
      int warnings = result.getWarnings().size();
      
      report.append(String.format("%-35s %s", result.getCheckName(), status));
      if (violations > 0 || warnings > 0) {
        report.append(String.format(" (%d violations, %d warnings)", violations, warnings));
      }
      report.append("\n");
      
      if (result.isPassed()) {
        passed++;
      }
    }

    report.append(String.format("\nOverall: %d/%d checks passed\n\n", passed, total));

    // Platform Readiness Score
    int readinessScore = calculateReadinessScore(results);
    report.append(String.format("Platform Readiness Score: %d/100\n", readinessScore));
    report.append(getReadinessAssessment(readinessScore)).append("\n\n");

    // Detailed Reports
    report.append("═══ DETAILED REPORTS ═══\n\n");
    
    report.append(apiBoundary.generateReport()).append("\n");
    report.append("─".repeat(60)).append("\n\n");
    
    report.append(asyncSafety.generateReport()).append("\n");
    report.append("─".repeat(60)).append("\n\n");
    
    report.append(configValidator.generateReport()).append("\n");
    report.append("─".repeat(60)).append("\n\n");
    
    report.append(serviceValidator.generateReport()).append("\n");
    report.append("─".repeat(60)).append("\n\n");
    
    report.append(foliaValidator.generateReport()).append("\n");
    report.append("─".repeat(60)).append("\n\n");
    
    report.append(dataProbe.generateReport()).append("\n");
    report.append("─".repeat(60)).append("\n\n");

    // Recommendations
    report.append("═══ RECOMMENDATIONS ═══\n\n");
    appendRecommendations(report, results);

    report.append("\n═══ END OF REPORT ═══\n");

    return report.toString();
  }

  private int calculateReadinessScore(ValidationResult[] results) {
    int score = 0;
    int maxScore = 100;
    int perCheckScore = maxScore / results.length;

    for (ValidationResult result : results) {
      if (result.isPassed()) {
        // Full points if no warnings
        if (result.getWarnings().isEmpty()) {
          score += perCheckScore;
        } else {
          // Partial credit with warnings
          score += (int)(perCheckScore * 0.8);
        }
      } else {
        // Partial credit based on violation severity
        int violations = result.getViolations().size();
        if (violations <= 3) {
          score += (int)(perCheckScore * 0.5);
        } else if (violations <= 10) {
          score += (int)(perCheckScore * 0.3);
        }
        // else 0 points
      }
    }

    return Math.min(score, maxScore);
  }

  private String getReadinessAssessment(int score) {
    if (score >= 90) {
      return "⭐ EXCELLENT - Platform ready for production deployment";
    } else if (score >= 75) {
      return "✓ GOOD - Minor issues to address before production";
    } else if (score >= 50) {
      return "⚠ FAIR - Significant issues require attention";
    } else {
      return "✗ POOR - Critical issues must be resolved";
    }
  }

  private void appendRecommendations(StringBuilder report, ValidationResult[] results) {
    List<String> recommendations = new ArrayList<>();

    for (ValidationResult result : results) {
      if (!result.isPassed() || !result.getWarnings().isEmpty()) {
        switch (result.getCheckName()) {
          case "API Boundary Compliance":
            if (!result.getViolations().isEmpty()) {
              recommendations.add("• Remove direct imports to zakum-core from feature modules");
              recommendations.add("• Refactor feature code to use only zakum-api interfaces");
            }
            break;
          case "Async/Threading Safety":
            if (!result.getWarnings().isEmpty()) {
              recommendations.add("• Review flagged blocking operations for async context");
              recommendations.add("• Migrate legacy BukkitScheduler usage to ZakumScheduler");
            }
            break;
          case "Configuration Immutability":
            if (!result.getViolations().isEmpty()) {
              recommendations.add("• Convert config classes to Java records for immutability");
              recommendations.add("• Remove setter methods from configuration classes");
            }
            break;
          case "Service Resolution":
            if (!result.getWarnings().isEmpty()) {
              recommendations.add("• Migrate plugins to extend ZakumPluginBase");
              recommendations.add("• Add null checks for ServicesManager.load() calls");
            }
            break;
          case "Folia Compatibility":
            if (!result.getWarnings().isEmpty()) {
              recommendations.add("• Replace global schedulers with runAtEntity/runAtLocation");
              recommendations.add("• Ensure entity operations use proper regional scheduling");
            }
            break;
          case "Data Schema Health":
            if (!result.getWarnings().isEmpty()) {
              recommendations.add("• Validate Flyway migration naming conventions");
              recommendations.add("• Test all schema migrations on clean database");
            }
            break;
        }
      }
    }

    if (recommendations.isEmpty()) {
      report.append("✓ No recommendations - platform is in excellent shape!\n");
    } else {
      report.append("Priority Actions:\n\n");
      for (String recommendation : recommendations) {
        report.append(recommendation).append("\n");
      }
    }
  }

  /**
   * Quick summary for CI/CD pipelines.
   */
  public String generateQuickSummary() {
    ApiBoundaryValidator apiBoundary = new ApiBoundaryValidator(projectRoot, featureModules);
    AsyncSafetyChecker asyncSafety = new AsyncSafetyChecker(projectRoot, allModules);
    ConfigSnapshotGenerator configValidator = new ConfigSnapshotGenerator(projectRoot, allModules);
    ServiceResolutionValidator serviceValidator = new ServiceResolutionValidator(projectRoot, allModules);
    FoliaCompatibilityValidator foliaValidator = new FoliaCompatibilityValidator(projectRoot, allModules);
    DataHealthProbe dataProbe = new DataHealthProbe(projectRoot, allModules);

    ValidationResult[] results = {
        apiBoundary.validate(),
        asyncSafety.validate(),
        configValidator.validate(),
        serviceValidator.validate(),
        foliaValidator.validate(),
        dataProbe.validate()
    };

    int passed = 0;
    for (ValidationResult result : results) {
      if (result.isPassed()) passed++;
    }

    int readinessScore = calculateReadinessScore(results);
    
    return String.format("Platform Verification: %d/%d checks passed | Readiness: %d/100", 
        passed, results.length, readinessScore);
  }
}
