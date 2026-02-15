package net.orbis.zakum.core.platform;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a platform validation check.
 */
public final class ValidationResult {
  private final String checkName;
  private final boolean passed;
  private final List<String> violations;
  private final List<String> warnings;

  public ValidationResult(String checkName, boolean passed, List<String> violations, List<String> warnings) {
    this.checkName = checkName;
    this.passed = passed;
    this.violations = new ArrayList<>(violations);
    this.warnings = new ArrayList<>(warnings);
  }

  public String getCheckName() {
    return checkName;
  }

  public boolean isPassed() {
    return passed;
  }

  public List<String> getViolations() {
    return new ArrayList<>(violations);
  }

  public List<String> getWarnings() {
    return new ArrayList<>(warnings);
  }

  public static Builder builder(String checkName) {
    return new Builder(checkName);
  }

  public static class Builder {
    private final String checkName;
    private final List<String> violations = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public Builder(String checkName) {
      this.checkName = checkName;
    }

    public Builder addViolation(String violation) {
      violations.add(violation);
      return this;
    }

    public Builder addWarning(String warning) {
      warnings.add(warning);
      return this;
    }

    public ValidationResult build() {
      return new ValidationResult(checkName, violations.isEmpty(), violations, warnings);
    }
  }
}
