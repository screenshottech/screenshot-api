-- Screenshot API Database Initialization Script

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- Kept, though not strictly necessary if all IDs are app-generated varchars

-- Users table
CREATE TABLE IF NOT EXISTS users (
                                     id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255), -- Was NOT NULL, Kotlin schema has nullable()
    credits_remaining INTEGER NOT NULL DEFAULT 0, -- Was DEFAULT 100, Kotlin schema has default(0)
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- Was VARCHAR(50), Kotlin schema has VARCHAR(20)
    plan_id VARCHAR(255) REFERENCES plans(id), -- Added explicit foreign key reference
    stripe_customer_id VARCHAR(100) NULL, -- Added from Kotlin schema
    last_activity TIMESTAMP WITH TIME ZONE NULL, -- Added from Kotlin schema
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

-- Plans table
CREATE TABLE IF NOT EXISTS plans (
                                     id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL, -- Kotlin schema has nullable()
    credits_per_month INTEGER NOT NULL,
    price_cents INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    features TEXT NULL, -- Was JSONB, Kotlin schema uses text("features").nullable() for JSON storage
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

-- API Keys table
CREATE TABLE IF NOT EXISTS api_keys (
                                        id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(50) NOT NULL,
    permissions TEXT NOT NULL, -- Was JSONB, Kotlin schema uses text("permissions"); consider a default if appropriate e.g. DEFAULT '[]'
    rate_limit INTEGER NOT NULL DEFAULT 1000,
    usage_count BIGINT NOT NULL DEFAULT 0, -- BIGINT matches Kotlin 'long'
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_used TIMESTAMP WITH TIME ZONE NULL,
    expires_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    -- updated_at removed as it's not in the Kotlin ApiKeys schema class
    );

-- Screenshots table
CREATE TABLE IF NOT EXISTS screenshots (
                                           id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_id VARCHAR(255) NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    url TEXT NOT NULL, -- Was VARCHAR(2048), Kotlin schema uses text()
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED', -- Was VARCHAR(50), Kotlin schema uses VARCHAR(20)
    result_url TEXT NULL, -- Was VARCHAR(2048), Kotlin schema uses text().nullable()
    options TEXT NOT NULL, -- Added from Kotlin schema to store JSON request details
    processing_time_ms BIGINT NULL, -- BIGINT matches Kotlin 'long'
    error_message TEXT NULL,
    webhook_url TEXT NULL, -- Was VARCHAR(2048), Kotlin schema uses text().nullable()
    webhook_sent BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE NULL
                                                           -- Removed: width, height, full_page, wait_time, wait_for_selector, quality, format (now part of 'options' JSON)
                                                           );

-- Activities table (aligned with Kotlin 'Activities' schema)
CREATE TABLE IF NOT EXISTS activities (
                                          id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- Was 'action VARCHAR(255)'
    description TEXT NOT NULL, -- Added from Kotlin schema
    metadata TEXT NULL, -- Was JSONB, Kotlin schema uses text("metadata").nullable(); also removed previous resource_type, resource_id
    ip_address VARCHAR(45) NULL, -- Was INET
    user_agent TEXT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW() -- Was 'created_at'
-- api_key_id removed as it's not in the Kotlin Activities schema
    );

-- Usage logs table (aligned with Kotlin 'UsageLogs' schema)
CREATE TABLE IF NOT EXISTS usage_logs (
                                          id VARCHAR(255) PRIMARY KEY, -- Was UUID, Kotlin schema uses VARCHAR for ID
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_id VARCHAR(255) REFERENCES api_keys(id) ON DELETE SET NULL,
    screenshot_id VARCHAR(255) REFERENCES screenshots(id) ON DELETE SET NULL, -- Added from Kotlin schema
    action VARCHAR(50) NOT NULL, -- Added from Kotlin schema
    credits_used INTEGER NOT NULL DEFAULT 0, -- Ensured NOT NULL based on Kotlin default
    metadata TEXT NULL, -- Added from Kotlin schema
    ip_address VARCHAR(45) NULL, -- Added from Kotlin schema
    user_agent TEXT NULL, -- Added from Kotlin schema
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW() -- Was 'created_at'; removed endpoint, method, status_code, response_time_ms
    );

-- Stripe customers table
CREATE TABLE IF NOT EXISTS stripe_customers (
                                                id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Kept UNIQUE constraint
    stripe_customer_id VARCHAR(100) UNIQUE NOT NULL, -- Was VARCHAR(255), Kotlin schema VARCHAR(100)
    subscription_id VARCHAR(100) NULL, -- Was VARCHAR(255), Kotlin schema VARCHAR(100)
    subscription_status VARCHAR(50) NULL,
    current_period_start TIMESTAMP WITH TIME ZONE NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false, -- Added from Kotlin schema
    trial_end TIMESTAMP WITH TIME ZONE NULL, -- Added from Kotlin schema
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

-- Create indexes for better performance (aligned with Kotlin Index objects)

-- For: val userEmailIndex = Index(listOf(Users.email), unique = true)
-- Note: The users.email column should also be defined with UNIQUE in CREATE TABLE.
-- This explicitly named index helps Exposed map it correctly.
CREATE UNIQUE INDEX IF NOT EXISTS "userEmailIndex" ON users(email);

-- For: val apiKeyHashIndex = Index(listOf(ApiKeys.keyHash), unique = true)
-- Note: The api_keys.key_hash column should also be defined with UNIQUE in CREATE TABLE.
CREATE UNIQUE INDEX IF NOT EXISTS "apiKeyHashIndex" ON api_keys(key_hash);

-- For: val screenshotUserIdIndex = Index(listOf(Screenshots.userId), unique = false)
CREATE INDEX IF NOT EXISTS "screenshotUserIdIndex" ON screenshots(user_id);

-- For: val screenshotStatusIndex = Index(listOf(Screenshots.status), unique = false)
CREATE INDEX IF NOT EXISTS "screenshotStatusIndex" ON screenshots(status);

-- For: val screenshotCreatedAtIndex = Index(listOf(Screenshots.createdAt), unique = false)
CREATE INDEX IF NOT EXISTS "screenshotCreatedAtIndex" ON screenshots(created_at);

-- For: val usageLogsUserIdIndex = Index(listOf(UsageLogs.userId), unique = false)
CREATE INDEX IF NOT EXISTS "usageLogsUserIdIndex" ON usage_logs(user_id);

-- For: val usageLogsTimestampIndex = Index(listOf(UsageLogs.timestamp), unique = false)
CREATE INDEX IF NOT EXISTS "usageLogsTimestampIndex" ON usage_logs(timestamp); -- Ensure this targets the 'timestamp' column

-- For: val activitiesUserIdIndex = Index(listOf(Activities.userId), unique = false)
CREATE INDEX IF NOT EXISTS "activitiesUserIdIndex" ON activities(user_id);

-- For: val activitiesTimestampIndex = Index(listOf(Activities.timestamp), unique = false)
CREATE INDEX IF NOT EXISTS "activitiesTimestampIndex" ON activities(timestamp); -- Ensure this targets the 'timestamp' column

-- For: val stripeCustomersUserIdIndex = Index(listOf(StripeCustomers.userId), unique = true)
CREATE INDEX IF NOT EXISTS idx_stripe_customers_user_id_explicit_for_non_warn ON stripe_customers(user_id); -- Added this to address the specific warning if you don't change Kotlin, ensure it's unique if it should be.


INSERT INTO plans (id, name, description, credits_per_month, price_cents, currency, features, is_active, created_at, updated_at) VALUES
                                                                                                                                     ('plan_free', 'Free Plan', 'Perfect for getting started', 100, 0, 'USD',
                                                                                                                                      '{"max_resolution": "1920x1080", "formats": ["PNG", "JPEG"], "support": "community"}', true, NOW(), NOW()),
                                                                                                                                     ('plan_basic', 'Basic Plan', 'For small projects and personal use', 1000, 999, 'USD',
                                                                                                                                      '{"max_resolution": "1920x1080", "formats": ["PNG", "JPEG", "PDF"], "support": "email"}', true, NOW(), NOW()),
                                                                                                                                     ('plan_pro', 'Pro Plan', 'For businesses and heavy usage', 10000, 4999, 'USD',
                                                                                                                                      '{"max_resolution": "4096x4096", "formats": ["PNG", "JPEG", "PDF"], "support": "priority"}', true, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

-- Insert development user
-- Note: stripe_customer_id and last_activity will be NULL for this user as they are not provided.
INSERT INTO users (id, email, password_hash, name, credits_remaining, status, plan_id, created_at, updated_at) VALUES
    ('user_123', 'dev@example.com', '$2a$10$rY8F8qX9Z1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3', 'Development User', 1000, 'ACTIVE', 'plan_basic', NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

-- Insert development API key
-- Note: updated_at column removed from api_keys table and this insert.
INSERT INTO api_keys (id, user_id, name, key_hash, key_prefix, permissions, rate_limit, usage_count, is_active, created_at) VALUES
    ('key_123', 'user_123', 'Development Key',
     '$2a$10$sk_development_test_key_123456789_hash', 'sk_dev',
     '["SCREENSHOT_CREATE", "SCREENSHOT_READ", "SCREENSHOT_LIST"]', 1000, 0, true, NOW())
    ON CONFLICT (id) DO NOTHING;

-- Create a function to update the updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers to automatically update updated_at
-- Only apply to tables that have an 'updated_at' column in their Kotlin schema
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_plans_updated_at BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
-- Trigger for api_keys removed as updated_at is not in its Kotlin schema
CREATE TRIGGER update_screenshots_updated_at BEFORE UPDATE ON screenshots
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_stripe_customers_updated_at BEFORE UPDATE ON stripe_customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
-- Activities and UsageLogs do not have an 'updated_at' field in their Kotlin schemas (they use 'timestamp')
