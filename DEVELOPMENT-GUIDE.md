# ZAKUM SUITE - DEVELOPMENT GUIDE

> **STANDARD:** This project is strictly developed using **IntelliJ IDEA (2024.1+)**.
> **JAVA VERSION:** Java 21 (Temurin/Adprium recommended).

## 1. The "Architect & Builder" Workflow

We have moved away from shell-script generation. 
- **You (The Architect):** Manage the IDE, run builds, and resolve imports.
- **AI (The Builder):** Generates specific class implementations to be pasted into the IDE.

## 2. Setting Up IntelliJ

1. **Open Project:** Launch IntelliJ -> \File\ -> \Open\ -> Select \C:\Zakum\zakum-v20\.
2. **Import Gradle:** Click "Load Gradle Project" when prompted.
3. **Verify SDK:** Go to \File\ -> \Project Structure\ -> \Project\. Ensure SDK is set to **21**.
4. **Sync:** Open the Gradle tab (right sidebar) and click the **Reload All Gradle Projects** (recycle icon).

## 3. Building the Suite

**DO NOT use \uild-all.bat\ for active development.** Use the IDE.

1. Open the **Gradle** tab on the right.
2. Expand \zakum-core\ -> \Tasks\ -> \uild\.
3. Double-click \uild\.
4. Fix any "red" errors in the editor (Alt+Enter to import missing classes).
5. Repeat for \zakum-crates\, \zakum-battlepass\, etc.

## 4. Common Troubleshooting

### "Package does not exist" (e.g., Micrometer, OkHttp)
* **Fix:** Click the **Reload Gradle Changes** button in the Gradle tab.

### "Byte Order Mark (BOM)" / "Illegal Character \ufeff"
* **Fix:** The file was saved with Windows encoding. Open file in IntelliJ -> Bottom Right -> Select \UTF-8\ -> \Convert\.

### "ExecutorService is not a functional interface"
* **Fix:** You cannot use lambdas (\ -> ...\) for \ExecutorService\. Pass the object directly.

## 5. Module Status
- **zakum-core:** 🟢 Stable (Needs IntelliJ Sync)
- **zakum-battlepass:** 🟢 Complete
- **zakum-crates:** 🟡 In Progress (Needs CrateAnimator implementation)
- **zakum-pets:** 🔴 Stubbed