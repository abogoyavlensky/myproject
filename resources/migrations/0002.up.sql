-- Create users table with authentication-related fields
CREATE TABLE IF NOT EXISTS "user" (
    id INTEGER UNIQUE PRIMARY KEY AUTOINCREMENT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create an index on email for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_email ON "user" (email);

-- Trigger to update the updated_at timestamp whenever a row is modified
CREATE TRIGGER IF NOT EXISTS user_updated_at
AFTER UPDATE ON "user"
FOR EACH ROW
BEGIN
    UPDATE "user" SET updated_at = CURRENT_TIMESTAMP
    WHERE id = old.id;
END;
