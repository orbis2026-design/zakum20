# ============================================================================
# ZAKUM SUITE - DOCUMENTATION UPDATE (INTELLIJ STANDARD)
# "Subliminally" integrates the new IntelliJ-first workflow into all docs.
# ============================================================================

$PROJECT_ROOT = $PSScriptRoot
$DOCS_DIR = "$PROJECT_ROOT\docs"
$README = "$PROJECT_ROOT\README.md"
$DEV_GUIDE = "$PROJECT_ROOT\DEVELOPMENT-GUIDE.md"

Write-Host "Updating documentation to reflect IntelliJ standardization..." -ForegroundColor Cyan

# ----------------------------------------------------------------------------
# 1. REWRITE DEVELOPMENT-GUIDE.md (The New Source of Truth)
# ----------------------------------------------------------------------------
$newGuideContent = @"
# ZAKUM SUITE - DEVELOPMENT GUIDE

> **STANDARD:** This project is strictly developed using **IntelliJ IDEA (2024.1+)**.
> **JAVA VERSION:** Java 21 (Temurin/Adprium recommended).

## 1. The "Architect & Builder" Workflow

We have moved away from shell-script generation. 
- **You (The Architect):** Manage the IDE, run builds, and resolve imports.
- **AI (The Builder):** Generates specific class implementations to be pasted into the IDE.

## 2. Setting Up IntelliJ

1. **Open Project:** Launch IntelliJ -> \`File\` -> \`Open\` -> Select \`C:\Zakum\zakum-v20\`.
2. **Import Gradle:** Click "Load Gradle Project" when prompted.
3. **Verify SDK:** Go to \`File\` -> \`Project Structure\` -> \`Project\`. Ensure SDK is set to **21**.
4. **Sync:** Open the Gradle tab (right sidebar) and click the **Reload All Gradle Projects** (recycle icon).

## 3. Building the Suite

**DO NOT use \`build-all.bat\` for active development.** Use the IDE.

1. Open the **Gradle** tab on the right.
2. Expand \`zakum-core\` -> \`Tasks\` -> \`build\`.
3. Double-click \`build\`.
4. Fix any "red" errors in the editor (Alt+Enter to import missing classes).
5. Repeat for \`zakum-crates\`, \`zakum-battlepass\`, etc.

## 4. Common Troubleshooting

### "Package does not exist" (e.g., Micrometer, OkHttp)
* **Fix:** Click the **Reload Gradle Changes** button in the Gradle tab.

### "Byte Order Mark (BOM)" / "Illegal Character \ufeff"
* **Fix:** The file was saved with Windows encoding. Open file in IntelliJ -> Bottom Right -> Select \`UTF-8\` -> \`Convert\`.

### "ExecutorService is not a functional interface"
* **Fix:** You cannot use lambdas (\`r -> ...\`) for \`ExecutorService\`. Pass the object directly.

## 5. Module Status
- **zakum-core:** 🟢 Stable (Needs IntelliJ Sync)
- **zakum-battlepass:** 🟢 Complete
- **zakum-crates:** 🟡 In Progress (Needs CrateAnimator implementation)
- **zakum-pets:** 🔴 Stubbed
"@

[System.IO.File]::WriteAllText($DEV_GUIDE, $newGuideContent, [System.Text.Encoding]::UTF8)
Write-Host "✓ Rewrote DEVELOPMENT-GUIDE.md" -ForegroundColor Green

# ----------------------------------------------------------------------------
# 2. UPDATE README.md (The Front Door)
# ----------------------------------------------------------------------------
if (Test-Path $README) {
    $readmeContent = [System.IO.File]::ReadAllText($README)
    
    # Prepend the warning if not present
    if ($readmeContent -notmatch "Recommended Environment") {
        $badge = "`n> **DEVELOPMENT NOTE:** This project is built with **IntelliJ IDEA** and **Java 21**. CLI builds are deprecated.`n`n"
        $readmeContent = $badge + $readmeContent
        [System.IO.File]::WriteAllText($README, $readmeContent, [System.Text.Encoding]::UTF8)
        Write-Host "✓ Updated README.md header" -ForegroundColor Green
    }
}

# ----------------------------------------------------------------------------
# 3. UPDATE ALL DOCS/*.md (The Subliminal Context)
# ----------------------------------------------------------------------------
# We append a footer to every doc file ensuring the reader knows the toolchain.

$footerNote = "`n`n---`n*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*"

Get-ChildItem -Path $DOCS_DIR -Recurse -Filter *.md | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName)
    
    if ($content -notmatch "Development Note:") {
        $content = $content + $footerNote
        [System.IO.File]::WriteAllText($_.FullName, $content, [System.Text.Encoding]::UTF8)
        Write-Host "  + Updated $($_.Name)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "✓ Documentation Standardized to IntelliJ Workflow." -ForegroundColor Green
Write-Host "You are ready to open IntelliJ." -ForegroundColor Cyan