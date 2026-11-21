# Repository profile - <KEY>

Every row cites its evidence as `path` or `path:line`. Use `UNKNOWN` when there is no evidence.

## 1. Overview
- Greenfield (no source, no manifest): yes / no
- Repository purpose (from docs): <one line> - evidence: <path>
- Layout: single project / multi-module / monorepo - evidence: <path>
- Default and protected branches: <names> - evidence: <path or git>

## 2. Technology inventory
| Module / path | Languages | Frameworks / major libraries | Evidence |
|---|---|---|---|
| <path> | <...> | <...> | <manifest path> |

## 3. Commands
| Purpose | Command | Working dir | Provenance (file:line) | Confidence |
|---|---|---|---|---|
| Install dependencies | <command or UNKNOWN> | | | |
| Build / compile | | | | |
| Unit tests | | | | |
| Integration tests | | | | |
| Lint / static analysis | | | | |
| Format | | | | |
| Type check | | | | |
| Security / dependency audit | | | | |
| Run locally | | | | |

## 4. Conventions
- Source layout and naming: <summary> - evidence: <paths>
- Error handling and logging: <summary> - evidence: <paths>
- Test layout, naming, fixtures: <summary> - evidence: <paths>
- Formatter / linter rules that apply: <summary> - evidence: <config paths>
- Commit message and branch naming style: <summary> - evidence: <git log / CONTRIBUTING>
- Documentation locations and changelog convention: <paths>

## 5. CI / CD
- Pipelines: <paths> - what they run: <summary>
- Required checks before merge: <summary or UNKNOWN>

## 6. External services and configuration
- Services, databases, queues, APIs the code talks to: <list with evidence>
- Configuration mechanism (files, env vars - names only, never values): <summary>

## 7. Open questions
| # | Question | Why it matters | Blocks which stage |
|---|---|---|---|

Confidence summary: sections 2-5 = <high | medium | low>
