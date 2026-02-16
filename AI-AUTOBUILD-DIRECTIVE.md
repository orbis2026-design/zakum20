# AI Auto-Build Directive

This document outlines the standard operating procedure for AI-driven automated builds within this repository.

## Manager-Worker Concept

The AI operates under a manager-worker paradigm. The manager, which is the AI itself, generates tasks and instructions. The worker is the automated build environment that executes these instructions and provides feedback.

## Worker Responsibilities

- **Unified Diff Output:** All changes made by the worker must be presented as a unified diff patch, adhering to the standard `diff --git a/... b/...` format.
- **Gradle Gate:** The primary build and validation command is `./gradlew :zakum-core:build`. This command must pass for any proposed changes to be considered valid.
- **Secret Management:** Under no circumstances should secrets such as API keys, passwords, or SSH private keys be committed to the repository. This includes but is not limited to files like `.env`, `.env.local`, and any files containing sensitive credentials.

## Acceptance Criteria
