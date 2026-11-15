# framework-scanner

An offline scanner for **Java test automation frameworks** (Playwright, REST Assured, Cucumber, TestNG/JUnit).
Point it at a framework's source code and it produces:

1. **A library catalog** – every class and method, what it does (from Javadoc or worked out from the code), its role
   (page object, API client, step definition, utility…), how often it is called and how often each Cucumber step is
   used in `.feature` files.
2. **Duplicate methods** – copies of the same helper scattered across classes, with a suggestion of which one to
   keep and which ones to replace.
3. **Best-practice recommendations** – 32 rules covering flaky waits, error handling, design, naming, REST Assured,
   Playwright and test design.

Everything runs locally: no network, no server. Source code is parsed with [JavaParser](https://javaparser.org/).
If `git` is available, authors are added to methods and findings via `git blame`.

---

## Requirements

- Java **17+**
- Maven **3.8+** (to build)
- `git` on the `PATH` (optional, only for author information)

## Build

```sh
mvn clean package
```

This produces a single runnable jar: `target/framework-scanner.jar`.

## Quick start (try the sample)

The repo includes a small, deliberately messy framework in `samples/legacy-framework` and a config for it:

```sh
java -jar target/framework-scanner.jar --config samples/scanner.yaml
```

Reports are written to `samples/scanner-report/`. Open `samples/scanner-report/report.html` in a browser.

## Scanning your own framework

1. Copy `samples/scanner.yaml` somewhere (for example next to your framework) and edit it. At minimum set
   `project.root` and, ideally, `library.includePackages`:

   ```yaml
   project:
     name: My Framework
     root: ../my-test-framework     # relative to this YAML file, or absolute
   library:
     includePackages:
       - com.mycompany.tests
   ```

2. Run:

   ```sh
   java -jar framework-scanner.jar --config path/to/scanner.yaml
   ```

   If `--config` is omitted, `scanner.yaml` in the current directory is used.

All relative paths in the YAML are resolved against the folder that contains the YAML file, so the command works
from any working directory.

## Command-line options

```
Usage: java -jar framework-scanner.jar [options]
  --config, -c FILE          scanner config (default: scanner.yaml)
  --fail-on-duplicates       exit with code 2 if any duplicate group is found
  --fail-on-severity LEVEL   exit with code 3 if a finding is at LEVEL or above (HIGH, MEDIUM, LOW, INFO)
  --help, -h                 show this help
```

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | Scan finished (no failure condition hit) |
| 1 | Error – bad arguments, bad config, I/O error |
| 2 | Duplicates found and `--fail-on-duplicates` was given |
| 3 | Findings at or above `--fail-on-severity` |

These make it easy to use as a CI quality gate, e.g.:

```sh
java -jar framework-scanner.jar -c scanner.yaml --fail-on-severity HIGH
```

## Output

Written to `report.outputDir` (default `scanner-report/`, relative to the YAML file). Choose formats with
`report.formats`.

| File | Contents |
|------|----------|
| `report.html` | Interactive report: catalog, duplicate groups, recommendations, rule list |
| `scan-result.json` | The full scan result, for tooling |
| `catalog.csv` | One row per method: package, class, role, signature, description, purpose, step text, call count, feature usages, author, file/line |
| `duplicates.csv` | One row per method in a duplicate group, with level, **KEEP** / *replace* and the reason for the choice |
| `recommendations.csv` | One row per finding: severity, category, rule, message, file/line, author |

A short summary is also printed to the console.

## How duplicates are detected

Methods smaller than `duplicates.minStatements` / `minTokens` (getters, setters, one-liners) are ignored. The rest
are compared and grouped at four levels, strongest first:

| Level | Meaning |
|-------|---------|
| **Exact copy** | Same code; only formatting or comments differ |
| **Renamed copy** | Same structure; variable/parameter/field names and literal values differ |
| **Near copy** | Mostly the same code (≥ `nearMissThreshold` of code fragments identical) |
| **Same job** | Different code, but the same UI/API actions on the same elements or endpoints, in the same order (≥ `sameJobThreshold`) |

For "same job", each method is reduced to a sequence of actions – navigate, click, input, read, wait, hard wait,
API request/setup, assert, call – resolved against constants where possible. In each group one method is marked
**KEEP** (preferring documented, public methods in shared classes, and ones that are called more); the others are
marked for replacement.

## Rules

Severity: **HIGH** – causes flaky/wrong tests or breaks parallel runs · **MEDIUM** – hurts reuse/maintenance ·
**LOW** – style/readability · **INFO** – suggestion.

| Category | Rule ID | Severity | Checks |
|----------|---------|----------|--------|
| Synchronisation | `HARD_WAIT` | HIGH | No fixed waits (`Thread.sleep`, `waitForTimeout`) |
| Design | `STATIC_MUTABLE_STATE` | HIGH | No static mutable Playwright/REST state |
| Error handling | `EMPTY_CATCH` | HIGH | No empty catch blocks |
| Error handling | `BROAD_CATCH_SWALLOWED` | MEDIUM | Do not swallow broad exceptions |
| Design | `LONG_METHOD` | MEDIUM | Short methods (`longMethodLines`) |
| Design | `TOO_MANY_PARAMETERS` | MEDIUM | Few parameters (`maxParameters`) |
| Design | `GOD_CLASS` | MEDIUM | One responsibility per class (`godClassMethods`) |
| Configuration | `HARDCODED_URL` | MEDIUM | No hard-coded URLs |
| Naming | `NON_DESCRIPTIVE_NAME` | MEDIUM | Names say what the method does |
| REST Assured | `REST_REPEATED_REQUEST_SETUP` | MEDIUM | Shared setup via `RequestSpecBuilder` |
| REST Assured | `REST_RESPONSE_NOT_CHECKED` | MEDIUM | Responses are checked |
| Playwright | `PLAYWRIGHT_DEPRECATED_API` | MEDIUM | No deprecated Playwright APIs |
| Test design | `LOCATOR_IN_STEP_DEFINITION` | MEDIUM | No locators in step definitions |
| Test design | `DUPLICATE_LOCATOR` | MEDIUM | One locator, one place |
| Test design | `THEN_STEP_WITHOUT_ASSERTION` | MEDIUM | `Then` steps assert something |
| Test design | `TEST_WITHOUT_ASSERTION` | MEDIUM | Tests assert something |
| Test design | `BRITTLE_XPATH` | MEDIUM | No absolute or positional XPath |
| Java idioms | `UTILITY_CLASS_INSTANTIABLE` | LOW | Utility classes are final with a private constructor |
| Java idioms | `REPEATED_LITERAL` | LOW | Repeated strings as constants/enums (`repeatedLiteralCount`) |
| Java idioms | `STRING_IF_CHAIN` | LOW | Enum + switch instead of if-else on strings (`stringIfChainLength`) |
| Java idioms | `RETURN_NULL` | LOW | `Optional` instead of returning null |
| Java idioms | `STRING_CONCAT_IN_LOOP` | LOW | `StringBuilder` / `String.join` in loops |
| Logging | `PRINT_STACK_TRACE` | LOW | Log exceptions instead of `printStackTrace` |
| Logging | `SYSTEM_OUT` | LOW | Logger instead of `System.out` |
| Playwright | `PLAYWRIGHT_PAGE_SELECTOR_METHOD` | LOW | Locators instead of `page.click(selector)` |
| Test design | `UNUSED_STEP_DEFINITION` | LOW | No unused step definitions |
| Naming | `CLASS_NAMING` | LOW | PascalCase classes |
| Naming | `METHOD_NAMING` | LOW | camelCase methods |
| Naming | `CONSTANT_NAMING` | LOW | UPPER_SNAKE_CASE constants |
| Naming | `VARIABLE_NAMING` | LOW | camelCase fields/parameters/variables |
| Naming | `PACKAGE_NAMING` | LOW | Lower-case packages |
| Naming | `METHOD_NAME_VERB` | INFO | Method names start with a verb |

Switch rules off with `rules.disabled: [RULE_ID, ...]`.

## Configuration reference

Every section has defaults; a minimal file only needs `project.root`.

```yaml
project:
  name: Test automation framework   # report title
  root: .                           # framework root (relative to this file or absolute)

library:                            # code that is catalogued, compared and checked
  sourceDirs: [src/main/java, src/test/java]   # relative to project.root
  includePackages: []               # empty = all packages (sub-packages included)
  excludePackages: []               # wins over includePackages
  excludePaths: []                  # globs, e.g. "**/generated/**"
  includePrivateMethods: true

usages:
  sourceDirs: []                    # where to count calls; empty = library.sourceDirs

features:
  dirs: [src/test/resources]        # .feature files for step usage; empty = off

duplicates:
  enabled: true
  minStatements: 2
  minTokens: 25
  nearMissThreshold: 0.80
  sameJobThreshold: 0.85
  minActions: 2

rules:
  enabled: true
  disabled: []
  longMethodLines: 40
  maxParameters: 4
  godClassMethods: 30
  repeatedLiteralCount: 3
  stringIfChainLength: 3

report:
  outputDir: scanner-report         # relative to this file
  formats: [html, json, csv]

git:
  blame: true                       # needs git and project.root inside a repository
```

## Project layout

```
src/main/java/com/frameworkscanner/
  Main.java, Scanner.java   CLI entry point and scan pipeline
  config/                   scanner.yaml loading
  parse/                    JavaParser-based source parsing
  analysis/                 action extraction, descriptions, call & step usage counts
  duplicates/               token normalisation and clone detection
  rules/                    best-practice rule sets and engine
  git/                      git blame integration
  report/                   HTML, JSON, CSV and console output
src/main/resources/report-template.html
samples/                    example framework, config and generated report
```
