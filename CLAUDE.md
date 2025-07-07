# CLAUDE.md

# Development rules

- Always use single semicolon ; for comments
- Commit messages should contain just one line


# Project Info

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

This is a Clojure web application using Babashka for task management. Key commands:

- `bb check` - Run all code checks (formatting, linting, outdated deps, tests)
- `bb test` - Run tests
- `bb fmt` - Fix code formatting

## Architecture

**Stack**: Clojure, Reitit (routing), Ring (HTTP), Integrant (system management), SQLite, TailwindCSS, HTMX, Alpine.js

**Core Components**:
- `myproject.core` - Main entry point
- `myproject.server` - HTTP server configuration
- `myproject.db` - Database connection and queries
- `myproject.routes` - URL routing and middleware
- `myproject.handlers` - Request handlers
- `myproject.views` - HTML view rendering
- `myproject.auth.*` - Authentication system (handlers, queries, specs, views)

**Authentication System**:
- Session-based auth using Buddy
- Routes: `/register`, `/login`, `/logout`, `/account`, `/forgot-password`, `/reset-password`
- Middleware: `wrap-login-required`, `wrap-already-logged-in`
- Password hashing with buddy-hashers

**Database**:
- SQLite for development (`db/myproject.sqlite`)
- In-memory SQLite for tests
- Migrations in `resources/migrations/`
- Uses HoneySQL for query building

**Frontend**:
- Server-side rendered HTML with HTMX for interactivity
- TailwindCSS for styling
- Alpine.js for client-side behavior
- Assets versioned and hashed for production

**Configuration**:
- Integrant for dependency injection
- Profile-based config (dev/test/prod) in `resources/config.edn`
- Environment-specific settings via `#profile` reader macro

**Testing**:
- Tests use eftest runner
- Test utilities in `test/myproject/test_utils.clj`
- Separate test config profile with in-memory database

**Build & Deploy**:
- Dockerized deployment
- Kamal for deployment orchestration
- Asset hashing and minification for production
- Uberjar build via slim library