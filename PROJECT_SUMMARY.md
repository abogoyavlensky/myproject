# MyProject - Clojure Web Application

## Overview
MyProject is a full-stack web application built with Clojure, featuring user authentication, SQLite database integration, and modern web development tools. The application is generated with [clojure-stack-lite](https://github.com/abogoyavlensky/clojure-stack-lite) and follows modern Clojure web development patterns using Integrant for system management, Reitit for routing, and Hiccup for HTML rendering.

## Architecture

### Core Technologies
- **Backend**: Clojure 1.12.0 with Ring/Jetty server
- **Routing**: Reitit with Malli for schema validation
- **Database**: SQLite with HikariCP connection pooling
- **Authentication**: Buddy (hashers, auth, sign) with session-based auth
- **System Management**: Integrant for dependency injection and lifecycle
- **Frontend**: Server-side rendered HTML with HTMX and AlpineJS
- **Styling**: TailwindCSS with auto-compilation
- **Asset Management**: Hashed assets for production with manifest tracking

### Key File Structure

#### Core Application Files
- `src/myproject/core.clj` - Application entry point and main function
- `src/myproject/server.clj` - Ring server configuration, middleware stack, and Integrant system setup
- `src/myproject/routes.clj` - Route definitions with auth middleware wrappers
- `src/myproject/handlers.clj` - Main request handlers for core functionality
- `src/myproject/views.clj` - Hiccup-based HTML view components with TailwindCSS
- `src/myproject/db.clj` - Database connection and query utilities using next.jdbc and HoneySQL

#### Authentication Module
- `src/myproject/auth/handlers.clj` - Authentication request handlers (login, register, logout)
- `src/myproject/auth/views.clj` - Authentication-specific UI components
- `src/myproject/auth/queries.clj` - User database operations
- `src/myproject/auth/spec.clj` - Malli schemas for auth data validation

#### Configuration & Resources
- `deps.edn` - Project dependencies and tool aliases
- `bb.edn` - Babashka task definitions for development workflow
- `resources/config.edn` - Environment-specific configuration with profiles
- `resources/config.dev.edn` - Development environment overrides
- `resources/migrations/` - Ragtime database migrations (0001.up.sql, 0002.up.sql)

#### Frontend Assets
- `resources/public/css/` - TailwindCSS input/output files
- `resources/public/js/` - Vendored JavaScript libraries (HTMX, AlpineJS)
- `resources/public/images/` - Application icons and static images
- `resources-hashed/` - Production assets with content-based hashing

#### Development & Testing
- `dev/user.clj` - Development REPL utilities and system management
- `test/myproject/` - Test suite with utilities for web testing
- `test/myproject/test_utils.clj` - Shared testing utilities including CSRF handling

## Dependencies

### Core Dependencies
- `integrant/integrant 0.13.1` - System lifecycle and dependency injection
- `metosin/reitit-* 0.8.0` - Routing, middleware, and schema validation
- `ring/ring-jetty-adapter 1.14.1` - HTTP server
- `buddy/* 2.0.167+` - Authentication and security
- `hikari-cp/hikari-cp 3.2.0` - Database connection pooling
- `org.xerial/sqlite-jdbc 3.49.1.0` - SQLite database driver
- `com.github.seancorfield/next.jdbc 1.3.1002` - Database access
- `com.github.seancorfield/honeysql 2.7.1295` - SQL query building

### Development Tools
- `eftest/eftest 0.6.0` - Test runner
- `cloverage/cloverage 1.2.4` - Test coverage
- `integrant/repl 0.4.0` - REPL-driven development
- `io.github.abogoyavlensky/slim 0.3.1` - Build tooling

## Available Commands (Babashka Tasks)

```bash
bb clj-repl          # Start Clojure REPL with system
bb check             # Run all checks (format, lint, outdated, test)
bb test              # Run test suite
bb fmt               # Fix code formatting
bb lint              # Run clj-kondo linting
bb css-watch         # Watch and rebuild CSS changes
bb css-build         # Build minified CSS for production
bb fetch-assets      # Download/update JavaScript dependencies
bb build             # Build production uberjar
bb kamal <command>   # Deploy using Kamal
```

## Development Workflow

### Starting Development
1. Install dependencies: `bb deps`
2. Start REPL: `bb clj-repl`
3. Load system: `(reset)` in REPL
4. Server runs at `http://localhost:8000`
5. CSS auto-recompiles on changes

### System Management in REPL
The application uses Integrant for system lifecycle. In the development REPL:
- `(reset)` - Restart the entire system
- `(halt)` - Stop the system
- `(go)` - Start the system

### Testing
- `bb test` - Run full test suite
- Tests use in-memory SQLite and include web integration tests
- CSRF token handling utilities available in `test-utils.clj`

## Key Patterns & Conventions

### Request Handling
- Handlers receive Ring request maps with additional context
- Database connection available as `(:db context)`
- Router accessible as `:reitit.core/router`
- Session-based authentication with `:identity` key

### Database Operations
- Use HoneySQL for query building
- Database functions in dedicated `queries.clj` namespaces
- Migrations managed by Ragtime in `resources/migrations/`

### View Rendering
- Server-side HTML rendering with Hiccup
- TailwindCSS for styling
- HTMX for dynamic interactions
- AlpineJS for client-side behavior
- Use `ext/render-html` for converting Hiccup to responses

### Authentication Flow
- Registration creates hashed passwords using Buddy
- Session-based authentication (no JWTs by default)
- Middleware wrappers for protected routes
- CSRF protection enabled by default

### Asset Management
- Static assets in `resources/public/`
- Production builds create hashed versions in `resources-hashed/`
- Manifest tracking for cache-busting

## Extension Points

### Adding New Routes
1. Define routes in `src/myproject/routes.clj`
2. Create handlers in appropriate handler namespace
3. Add views in corresponding view namespace
4. Include any required middleware wrappers

### Database Changes
1. Create new migration file in `resources/migrations/`
2. Add queries to appropriate `queries.clj` namespace
3. Update specs if needed for validation

### New Authentication Features
- Extend `auth/` module with additional handlers
- Add new Malli specs in `auth/spec.clj`
- Consider JWT tokens for API endpoints if needed

### Frontend Enhancements
- Add new TailwindCSS classes (auto-compiled)
- Include additional JavaScript libraries via `bb fetch-assets`
- Use HTMX attributes for dynamic behavior
- Add AlpineJS components for complex interactions

## Configuration

The application uses profile-based configuration:
- `:default` - Base configuration
- `:dev` - Development overrides (auto-reload, different ports)
- `:test` - Test environment (in-memory DB)
- `:prod` - Production settings (environment variables)

Environment variables for production:
- `SESSION_SECRET_KEY` - Required for session security
- Database URL configurable via profiles

## Deployment

### Local Development
- Use `bb clj-repl` and `(reset)` for REPL-driven development
- TailwindCSS watches for changes automatically

### Production (Kamal)
- Configure `.env` file with server details
- Use `bb kamal setup` for initial deployment
- Use `bb kamal deploy` for updates
- Supports GitHub Actions deployment

### Build Process
1. `bb css-build` - Compile and minify CSS
2. Asset hashing for cache-busting
3. Uberjar creation with `bb build`
4. Docker deployment via Kamal

This architecture provides a solid foundation for building modern Clojure web applications with authentication, database persistence, and contemporary frontend tooling.
