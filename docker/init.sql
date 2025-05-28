-- Screenshot API Database Initialization Script

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    credits_remaining INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    plan_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Plans table
CREATE TABLE IF NOT EXISTS plans (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    credits_per_month INTEGER NOT NULL,
    price_cents INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    features JSONB,
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
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Screenshots table
CREATE TABLE IF NOT EXISTS screenshots (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_id VARCHAR(255) NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    url VARCHAR(2048) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    full_page BOOLEAN NOT NULL DEFAULT false,
    wait_time INTEGER,
    wait_for_selector VARCHAR(255),
    quality INTEGER NOT NULL DEFAULT 80,
    format VARCHAR(10) NOT NULL DEFAULT 'PNG',
    result_url VARCHAR(2048),
    webhook_url VARCHAR(2048),
    webhook_sent BOOLEAN NOT NULL DEFAULT false,
    processing_time_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Activities table
CREATE TABLE IF NOT EXISTS activities (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_id VARCHAR(255) REFERENCES api_keys(id) ON DELETE SET NULL,
    action VARCHAR(255) NOT NULL,
    resource_type VARCHAR(255),
    resource_id VARCHAR(255),
    metadata JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Usage logs table
CREATE TABLE IF NOT EXISTS usage_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_id VARCHAR(255) REFERENCES api_keys(id) ON DELETE SET NULL,
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INTEGER NOT NULL,
    response_time_ms INTEGER,
    credits_used INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Stripe customers table (for billing)
CREATE TABLE IF NOT EXISTS stripe_customers (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stripe_customer_id VARCHAR(255) UNIQUE NOT NULL,
    subscription_id VARCHAR(255),
    subscription_status VARCHAR(50),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_key_hash ON api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_api_keys_is_active ON api_keys(is_active);
CREATE INDEX IF NOT EXISTS idx_screenshots_user_id ON screenshots(user_id);
CREATE INDEX IF NOT EXISTS idx_screenshots_status ON screenshots(status);
CREATE INDEX IF NOT EXISTS idx_screenshots_created_at ON screenshots(created_at);
CREATE INDEX IF NOT EXISTS idx_activities_user_id ON activities(user_id);
CREATE INDEX IF NOT EXISTS idx_activities_created_at ON activities(created_at);
CREATE INDEX IF NOT EXISTS idx_usage_logs_user_id ON usage_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_usage_logs_created_at ON usage_logs(created_at);

-- Insert default plans
INSERT INTO plans (id, name, description, credits_per_month, price_cents, currency, features, is_active) VALUES
('plan_free', 'Free Plan', 'Perfect for getting started', 100, 0, 'USD', 
 '{"max_resolution": "1920x1080", "formats": ["PNG", "JPEG"], "support": "community"}', true),
('plan_basic', 'Basic Plan', 'For small projects and personal use', 1000, 999, 'USD', 
 '{"max_resolution": "1920x1080", "formats": ["PNG", "JPEG", "PDF"], "support": "email"}', true),
('plan_pro', 'Pro Plan', 'For businesses and heavy usage', 10000, 4999, 'USD', 
 '{"max_resolution": "4096x4096", "formats": ["PNG", "JPEG", "PDF"], "support": "priority"}', true)
ON CONFLICT (id) DO NOTHING;

-- Insert development user
INSERT INTO users (id, email, password_hash, name, credits_remaining, status, plan_id) VALUES
('user_123', 'dev@example.com', '$2a$10$rY8F8qX9Z1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3', 'Development User', 1000, 'ACTIVE', 'plan_basic')
ON CONFLICT (id) DO NOTHING;

-- Insert development API key
INSERT INTO api_keys (id, user_id, name, key_hash, key_prefix, is_active) VALUES
('key_123', 'user_123', 'Development Key', 
 '$2a$10$sk_development_test_key_123456789_hash', 'sk_dev', true)
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
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_plans_updated_at BEFORE UPDATE ON plans 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_api_keys_updated_at BEFORE UPDATE ON api_keys 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_screenshots_updated_at BEFORE UPDATE ON screenshots 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_stripe_customers_updated_at BEFORE UPDATE ON stripe_customers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();