# ACE Diagnostics Taxonomy

Purpose
- Provide structured, bounded diagnostics for ACE parse/execute failures.
- Keep script execution fault-tolerant while making root causes visible at runtime.

Config
- `operations.aceDiagnostics.enabled`
- `operations.aceDiagnostics.maxRecentEntries`
- `operations.aceDiagnostics.maxLineLength`

Taxonomy codes
- `PARSE_SYNTAX`: line does not match ACE parser pattern.
- `UNKNOWN_EFFECT`: effect key is not registered in the ACE runtime.
- `UNKNOWN_TARGETER`: targeter token is unknown (fallback target resolution used).
- `EFFECT_EXECUTION`: effect handler threw during execution.

Command surface
- `/zakum ace status`
- `/zakum ace errors [limit]`
- `/zakum ace clear`
- `/zakum ace enable`
- `/zakum ace disable`

Health integration
- `datahealth` includes:
  - `ace.diag.enabled`
  - `ace.diag.parseFailures`
  - `ace.diag.executionFailures`
  - `ace.diag.unknownEffects`
  - `ace.diag.unknownTargeters`

Notes
- Diagnostics are bounded in-memory to avoid unbounded growth.
- Execution remains fault-tolerant per effect line; failures are tracked and surfaced.
