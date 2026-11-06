# ADR 0001 — Start as a modular monolith

**Status:** Accepted (initial baseline)

**Context:** Solo developer, 8–10 weekly hours, web-first then mobile, learning across the stack.

**Decision:** Use one Spring Boot application with domain-oriented modules, synchronous REST, and PostgreSQL transactions.

**Why:** Lower operational burden, easier debugging and consistency, and enough complexity to learn clean architecture.

**Consequences:** Modules must maintain boundaries. Re-evaluate service extraction only when measurable operational or domain requirements justify it.
