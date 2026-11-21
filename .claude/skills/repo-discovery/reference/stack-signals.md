# Discovery hints (optional, non-authoritative)

Filename patterns that commonly indicate where to look. This file only helps you **find evidence faster**.

- It contains no commands. Commands must come from the repository itself (CI, docs, task files, manifests).
- It is not exhaustive. An unfamiliar file is identified by reading it.
- Extend it freely: add a row, no other change is needed anywhere in the framework.

| What you are looking for | Filename patterns to search (Glob) |
|---|---|
| Project instructions | `CLAUDE.md`, `AGENTS.md`, `README*`, `CONTRIBUTING*`, `docs/**`, `*.md` at root |
| CI/CD definitions | `.github/workflows/*`, `.gitlab-ci*`, `Jenkinsfile*`, `azure-pipelines*`, `.circleci/*`, `bitbucket-pipelines*`, `.buildkite/*`, `*.pipeline.*` |
| Task runners | `Makefile`, `justfile`, `Taskfile*`, `build.*`, `scripts/**`, `tools/**`, `bin/**` |
| Dependency / build manifests | `*.toml`, `*.lock`, `*.lockb`, `*.json` at module roots, `*.xml` build files, `*.gradle*`, `*.sbt`, `*.csproj`, `*.sln`, `*.fsproj`, `*.cabal`, `*.nimble`, `*.gemspec`, `Gemfile*`, `requirements*`, `setup.*`, `go.mod`, `Cargo.*`, `composer.*`, `pubspec.*`, `mix.exs`, `rebar.config`, `Package.swift`, `CMakeLists.txt`, `meson.build`, `BUILD*`, `WORKSPACE*`, `deno.*`, `*.nix`, `flake.*` |
| Containers / environments | `Dockerfile*`, `docker-compose*`, `compose*`, `.devcontainer/**`, `*.tf`, `helm/**`, `k8s/**` |
| Formatter / linter / type-check config | `.editorconfig`, `.*rc`, `.*rc.*`, `*.config.*`, `.prettier*`, `.eslint*`, `ruff*`, `pyproject*`, `tox.ini`, `setup.cfg`, `.golangci*`, `rustfmt*`, `clippy*`, `.rubocop*`, `phpcs*`, `phpstan*`, `checkstyle*`, `detekt*`, `.clang-*`, `tsconfig*`, `mypy*` |
| Test layout | directories named `test`, `tests`, `spec`, `__tests__`, `e2e`, `integration`; files with `test` or `spec` in the name |
| Database / migration | `migrations/**`, `db/**`, `schema.*`, `*.sql`, `prisma/**`, `alembic*` |
| API contracts | `openapi*`, `swagger*`, `*.proto`, `*.graphql`, `*.wsdl`, `asyncapi*` |
| Secrets (note existence only, never read) | `.env*`, `*.pem`, `*.key`, `credentials*`, `secrets*` |
