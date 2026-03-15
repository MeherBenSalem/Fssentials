# Java Upgrade Plan

**Session ID**: 20260315-upgrade  
**Project**: Fssentials  
**Generated**: 2026-03-15  
**Current Branch**: main  
**Current Commit**: d22de8b0f48a923127053a3680e678b0043d15f4

---

## Upgrade Goals

| Current Version | Target Version | Status |
|----------------|----------------|--------|
| Java 17        | Java 21        | ✅ Target defined |

---

## Guidelines

_No specific guidelines provided by user_

---

## Options

| Option | Value |
|--------|-------|
| Run tests before and after the upgrade | true |
| Working branch | appmod/java-upgrade-20260315-upgrade |

---

## Available Tools

| Tool | Version | Path | Notes |
|------|---------|------|-------|
| JDK 17 | 17.0.12 | `C:\Program Files\Java\jdk-17\bin` | Current version |
| JDK 21 | 21.0.9 | `C:\Program Files\Java\jdk-21\bin` | Target version - already installed |
| Maven | 3.9.12 | `D:\Libraries\Maven\3.9.12\bin` | From PATH |
| Maven Wrapper | N/A | Not present | Will use system Maven |

---

## Technology Stack

| Dependency | Current Version | Target Version | Compatibility | Notes |
|------------|----------------|----------------|---------------|-------|
| Java (compiler) | 17 | 21 | Compatible | Direct upgrade path |
| Paper API | 1.20.6-R0.1-SNAPSHOT | 1.20.6-R0.1-SNAPSHOT | Compatible | Supports Java 21 |
| Maven Compiler Plugin | 3.11.0 | 3.13.0 | Compatible | Recommended for Java 21 |
| Maven Shade Plugin | 3.5.1 | 3.6.0 | Compatible | Latest stable version |

---

## Derived Upgrades

| Dependency | Reason | From | To | Risk |
|------------|--------|------|-----|------|
| Maven Compiler Plugin | Improved Java 21 support | 3.11.0 | 3.13.0 | Low |
| Maven Shade Plugin | Bug fixes and improvements | 3.5.1 | 3.6.0 | Low |

---

## Key Challenges

- **Low complexity**: This is a straightforward Java 17 → 21 upgrade with minimal dependencies
- **Plugin compatibility**: Paper API 1.20.6 already supports Java 21
- **Single module**: Simple project structure reduces upgrade complexity
- **No breaking API changes**: Java 21 maintains backward compatibility with Java 17 bytecode

---

## Upgrade Steps

### Step 1: Setup Environment

**Objective**: Verify all required tools are available  
**Verification**: All tools detected and accessible  
**JDK Version**: Java 17 (current)  
**Estimated Time**: < 1 minute

**Changes**:
- Verify Java 21 is available (already installed)
- Verify Maven 3.9.12 is available
- No installation needed

---

### Step 2: Setup Baseline

**Objective**: Establish compilation and test baseline on Java 17  
**Verification**: `mvn clean test-compile` succeeds; document test results  
**JDK Version**: Java 17  
**Estimated Time**: 2-3 minutes

**Changes**:
- Stash any uncommitted changes
- Run `mvn clean test-compile` with Java 17
- Run `mvn clean test` to establish baseline test results
- Document compilation status and test pass rate

---

### Step 3: Upgrade Maven Plugins

**Objective**: Update build plugins for Java 21 compatibility  
**Verification**: `mvn clean test-compile` succeeds with Java 17  
**JDK Version**: Java 17  
**Estimated Time**: 2-3 minutes

**Changes**:
- Update maven-compiler-plugin: 3.11.0 → 3.13.0
- Update maven-shade-plugin: 3.5.1 → 3.6.0
- Verify build still works with Java 17

---

### Step 4: Upgrade Java Compiler Target

**Objective**: Update Java source/target to version 21  
**Verification**: `mvn clean test-compile` succeeds with Java 21  
**JDK Version**: Java 21  
**Estimated Time**: 3-5 minutes

**Changes**:
- Update `maven.compiler.source`: 17 → 21
- Update `maven.compiler.target`: 17 → 21
- Update maven-compiler-plugin `<release>`: 17 → 21
- Set JAVA_HOME to Java 21 path
- Run `mvn clean test-compile` with Java 21
- Document any compilation warnings or errors

---

### Step 5: Final Validation

**Objective**: Verify all upgrade goals met and achieve 100% test pass rate  
**Verification**: All tests pass, all goals achieved  
**JDK Version**: Java 21  
**Estimated Time**: 5-10 minutes

**Changes**:
- Run full test suite: `mvn clean test`
- Fix any test failures (iterative loop until 100% pass or ≥ baseline)
- Verify Java 21 is active: `mvn --version`
- Confirm all TODOs from previous steps are resolved
- Document final results and any limitations

---

## Plan Review

**Reviewed**: 2026-03-15

### Completeness Check
- ✅ All placeholders filled
- ✅ All upgrade goals covered
- ✅ Step sequence is logical and complete
- ✅ Verification criteria defined for each step
- ✅ Risk assessment included

### Feasibility Assessment
- ✅ All required tools available (Java 21 already installed)
- ✅ Direct upgrade path exists (Java 17 → 21)
- ✅ No known incompatibilities identified
- ✅ Paper API supports Java 21
- ✅ Simple project structure reduces risk

### Known Limitations
- None identified

### Revision History
- 2026-03-15: Initial plan created

---

_Plan ready for user confirmation._
