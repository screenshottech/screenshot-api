-- Screenshot API Database Initialization Script

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- Kept, though not strictly necessary if all IDs are app-generated varchars

create function public.update_updated_at_column() returns trigger
    language plpgsql
as
$$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$;

alter function public.update_updated_at_column() owner to screenshotapi_user;


create table public.plans
(
    id                      varchar(255)                                     not null
        primary key,
    name                    varchar(255)                                     not null,
    description             text,
    credits_per_month       integer                                          not null,
    price_cents_monthly     integer                                          not null,
    price_cents_annual      integer,
    billing_cycle           varchar(20) default 'monthly'::character varying not null,
    currency                varchar(10) default 'USD'::character varying     not null,
    features                text,
    is_active               boolean     default true                         not null,
    created_at              timestamp                                        not null,
    updated_at              timestamp                                        not null,
    stripe_product_id       varchar(100),
    stripe_price_id_monthly varchar(100),
    stripe_price_id_annual  varchar(100),
    stripe_metadata         text,
    sort_order              integer     default 0                            not null
);

alter table public.plans
    owner to screenshotapi_user;

create trigger update_plans_updated_at
    before update
    on public.plans
    for each row
    execute procedure public.update_updated_at_column();

create table public.users
(
    id                 varchar(255)                                    not null
        primary key,
    email              varchar(255)                                    not null
        unique,
    password_hash      varchar(255),
    name               varchar(255),
    credits_remaining  integer     default 0                           not null,
    status             varchar(20) default 'ACTIVE'::character varying not null,
    plan_id            varchar(255)                                    not null
        constraint fk_users_plan_id__id
            references public.plans
            on update restrict on delete restrict,
    stripe_customer_id varchar(100),
    last_activity      timestamp with time zone,
    first_screenshot_completed_at timestamp with time zone,
    created_at         timestamp                                       not null,
    updated_at         timestamp                                       not null,
    auth_provider      varchar(50) default 'local'::character varying  not null,
    external_id        varchar(255)
);

alter table public.users
    owner to screenshotuser;

create unique index "userEmailIndex"
    on public.users (email);

-- Index for first screenshot analytics and optimizations
create index idx_users_first_screenshot
    on public.users (first_screenshot_completed_at)
    where first_screenshot_completed_at is not null;

create trigger update_users_updated_at
    before update
    on public.users
    for each row
    execute procedure public.update_updated_at_column();

create table public.api_keys
(
    id          varchar(255)         not null
        primary key,
    user_id     varchar(255)         not null
        constraint fk_api_keys_user_id__id
            references public.users
            on update restrict on delete restrict,
    name        varchar(255)         not null,
    key_hash    varchar(255)         not null
        unique,
    key_prefix  varchar(50)          not null,
    permissions text                 not null,
    rate_limit  integer default 1000 not null,
    usage_count bigint  default 0    not null,
    is_active   boolean default true not null,
    is_default  boolean default false not null,
    last_used   timestamp with time zone,
    expires_at  timestamp with time zone,
    created_at  timestamp            not null
);

alter table public.api_keys
    owner to screenshotuser;

create unique index "apiKeyHashIndex"
    on public.api_keys (key_hash);

create index idx_api_keys_user_id
    on public.api_keys (user_id);

-- Unique constraint: only one default API key per user
create unique index idx_api_keys_user_default
    on public.api_keys (user_id)
    where is_default = true and is_active = true;

create table public.screenshots
(
    id                 varchar(255)          not null
        primary key,
    user_id            varchar(255)          not null
        constraint fk_screenshots_user_id__id
            references public.users
            on update restrict on delete restrict,
    api_key_id         varchar(255)          not null
        constraint fk_screenshots_api_key_id__id
            references public.api_keys
            on update restrict on delete restrict,
    url                text                  not null,
    status             varchar(20)           not null,
    result_url         text,
    file_size_bytes     bigint default 0 not null,
    options            text                  not null,
    processing_time_ms bigint,
    error_message      text,
    webhook_url        text,
    webhook_sent       boolean default false not null,
    created_at         timestamp             not null,
    updated_at         timestamp             not null,
    completed_at       timestamp with time zone,
    retry_count        integer default 0     not null,
    max_retries        integer default 3     not null,
    next_retry_at      timestamp with time zone,
    last_failure_reason text,
    is_retryable       boolean default true  not null,
    retry_type         varchar(20) default 'AUTOMATIC' not null,
    locked_by          varchar(255),
    locked_at          timestamp with time zone,
    metadata           text
);

alter table public.screenshots
    owner to screenshotuser;

create index "screenshotuserIdIndex"
    on public.screenshots (user_id);

create index "screenshotStatusIndex"
    on public.screenshots (status);

create index "screenshotCreatedAtIndex"
    on public.screenshots (created_at);

CREATE INDEX IF NOT EXISTS "idx_screenshots_user_created" ON screenshots(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS "idx_screenshots_user_status" ON screenshots(user_id, status);

CREATE INDEX IF NOT EXISTS "idx_screenshots_retry_ready" ON screenshots(next_retry_at) WHERE status = 'QUEUED' AND is_retryable = true AND next_retry_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS "idx_screenshots_stuck_jobs" ON screenshots(status, updated_at) WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS "idx_screenshots_failed_retryable" ON screenshots(status, is_retryable, retry_count, max_retries) WHERE status = 'FAILED' AND is_retryable = true;
CREATE INDEX IF NOT EXISTS "idx_screenshots_locked_jobs" ON screenshots(locked_by, locked_at) WHERE locked_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS "idx_screenshots_metadata_not_null" ON screenshots(id) WHERE metadata IS NOT NULL;

-- Add comment for metadata column
COMMENT ON COLUMN screenshots.metadata IS 'JSON-serialized PageMetadata containing SEO, performance, content, and social media data extracted during screenshot generation';

create trigger update_screenshots_updated_at
    before update
    on public.screenshots
    for each row
    execute procedure public.update_updated_at_column();

create table public.activities
(
    id          varchar(255) not null
        primary key,
    user_id     varchar(255) not null
        constraint fk_activities_user_id__id
            references public.users
            on update restrict on delete restrict,
    type        varchar(50)  not null,
    description text         not null,
    metadata    text,
    ip_address  varchar(45),
    user_agent  text,
    timestamp   timestamp    not null
);

alter table public.activities
    owner to screenshotuser;

create index "activitiesUserIdIndex"
    on public.activities (user_id);

create index "activitiesTimestampIndex"
    on public.activities (timestamp);

create table public.usage_logs
(
    id            varchar(255)      not null
        primary key,
    user_id       varchar(255)      not null
        constraint fk_usage_logs_user_id__id
            references public.users
            on update restrict on delete restrict,
    api_key_id    varchar(255)
        constraint fk_usage_logs_api_key_id__id
            references public.api_keys
            on update restrict on delete restrict,
    screenshot_id varchar(255)
        constraint fk_usage_logs_screenshot_id__id
            references public.screenshots
            on update restrict on delete restrict,
    action        varchar(50)       not null,
    credits_used  integer default 0 not null,
    metadata      text,
    ip_address    varchar(45),
    user_agent    text,
    timestamp     timestamp         not null
);

alter table public.usage_logs
    owner to screenshotuser;

create index "usageLogsUserIdIndex"
    on public.usage_logs (user_id);

create index "usageLogsTimestampIndex"
    on public.usage_logs (timestamp);

create table public.usage_tracking
(
    user_id            varchar(255)                           not null
        references public.users
            on delete cascade,
    month              varchar(7)                             not null,
    total_requests     integer                  default 0     not null,
    plan_credits_limit integer                                not null,
    remaining_credits  integer                                not null,
    last_request_at    timestamp with time zone               not null,
    created_at         timestamp with time zone default now() not null,
    updated_at         timestamp with time zone default now() not null,
    primary key (user_id, month)
);

alter table public.usage_tracking
    owner to screenshotuser;

create trigger update_usage_tracking_updated_at
    before update
    on public.usage_tracking
    for each row
    execute procedure public.update_updated_at_column();

create table public.stripe_customers
(
    id                   varchar(255)          not null
        primary key,
    user_id              varchar(255)          not null
        unique
        constraint fk_stripe_customers_user_id__id
            references public.users
            on update restrict on delete restrict,
    stripe_customer_id   varchar(100)          not null
        unique,
    subscription_id      varchar(100),
    subscription_status  varchar(50),
    current_period_start timestamp with time zone,
    current_period_end   timestamp with time zone,
    cancel_at_period_end boolean default false not null,
    trial_end            timestamp with time zone,
    created_at           timestamp             not null,
    updated_at           timestamp             not null
);

alter table public.stripe_customers
    owner to screenshotuser;

create index idx_stripe_customers_user_id_explicit_for_non_warn
    on public.stripe_customers (user_id);

create trigger update_stripe_customers_updated_at
    before update
    on public.stripe_customers
    for each row
    execute procedure public.update_updated_at_column();

create table public.subscriptions
(
    id                     varchar(255)                           not null
        primary key,
    user_id                varchar(255)                           not null
        references public.users
            on delete cascade,
    plan_id                varchar(255)                           not null
        references public.plans,
    billing_cycle          varchar(20)                            not null,
    status                 varchar(50)                            not null,
    stripe_subscription_id varchar(255),
    stripe_customer_id     varchar(255),
    current_period_start   timestamp with time zone               not null,
    current_period_end     timestamp with time zone               not null,
    cancel_at_period_end   boolean                  default false not null,
    created_at             timestamp with time zone default now() not null,
    updated_at             timestamp with time zone default now() not null
);

alter table public.subscriptions
    owner to screenshotuser;

create index "subscriptionsUserIdIndex"
    on public.subscriptions (user_id);

create index "subscriptionsStripeSubscriptionIdIndex"
    on public.subscriptions (stripe_subscription_id);

create index "subscriptionsStripeCustomerIdIndex"
    on public.subscriptions (stripe_customer_id);

create index "subscriptionsStatusIndex"
    on public.subscriptions (status);

-- Add unique constraint to prevent duplicate Stripe webhook processing
alter table public.subscriptions
    add constraint unique_stripe_subscription_id
        unique (stripe_subscription_id);

-- Add optimized index for webhook lookups (in addition to the basic index)
-- This partial index is more efficient for queries that filter out NULLs
create index if not exists idx_subscriptions_stripe_subscription_id_lookup
    on public.subscriptions (stripe_subscription_id)
    where stripe_subscription_id is not null;

-- Add documentation comments
comment on constraint unique_stripe_subscription_id on public.subscriptions
    is 'Ensures each Stripe subscription ID can only exist once, preventing duplicate webhook processing';

comment on index idx_subscriptions_stripe_subscription_id_lookup
    is 'Optimizes webhook processing by speeding up Stripe subscription ID lookups';

create trigger update_subscriptions_updated_at
    before update
    on public.subscriptions
    for each row
    execute procedure public.update_updated_at_column();


INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_starter_annual', 'Starter Annual', '12% cheaper + OCR included + 10% annual savings', 2000, 1499, 16200, 'annual', 'USD', '["All Free features", "15 req/min, 400/hour", "8 concurrent requests", "Usage analytics", "Priority support", "OCR text extraction", "Webhooks", "10% savings (2 months free)"]', true, 'prod_starter', 'price_starter_monthly', 'price_starter_annual', null, 2, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');
INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_pro_monthly', 'Professional Monthly', '13% cheaper + batch processing + analytics dashboard', 10000, 6900, null, 'monthly', 'USD', '["All Starter features", "25 req/min, 1500/hour", "20 concurrent requests", "Advanced analytics", "Batch processing", "Mobile simulation (coming soon)", "SLA guarantee"]', true, 'prod_professional', 'price_pro_monthly', 'price_pro_annual', null, 3, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');
INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_pro_annual', 'Professional Annual', '13% cheaper + batch processing + analytics + 10% annual savings', 10000, 6900, 74520, 'annual', 'USD', '["All Starter features", "25 req/min, 1500/hour", "20 concurrent requests", "Advanced analytics", "Batch processing", "Mobile simulation (coming soon)", "SLA guarantee", "10% savings (2 months free)"]', true, 'prod_professional', 'price_pro_monthly', 'price_pro_annual', null, 3, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');
INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_enterprise_monthly', 'Enterprise Monthly', '12% cheaper + unlimited requests + white-label + on-premise', 50000, 22900, null, 'monthly', 'USD', '["All Professional features", "100 req/min, 6000/hour", "Unlimited concurrent requests", "Video capture (coming soon)", "Custom CSS injection (coming soon)", "Scheduled screenshots (coming soon)", "Dedicated support"]', true, 'prod_enterprise', 'price_enterprise_monthly', 'price_enterprise_annual', null, 4, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');
INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_free', 'Free Forever', '3x more generous than competitors - perfect for developers', 300, 0, null, 'monthly', 'USD', '["High-quality screenshots", "PNG, JPEG, WEBP, PDF formats", "Full page capture", "Custom dimensions", "10 req/min, 300/hour", "5 concurrent requests", "API access"]', true, null, null, null, null, 1, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');
INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_starter_monthly', 'Starter Monthly', '12% cheaper than competitors + OCR included', 2000, 1499, null, 'monthly', 'USD', '["All Free features", "15 req/min, 400/hour", "8 concurrent requests", "Usage analytics", "Priority support", "OCR text extraction", "Webhooks"]', true, 'prod_starter', 'price_starter_monthly', 'price_starter_annual', null, 2, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');
INSERT INTO public.plans (id, name, description, credits_per_month, price_cents_monthly, price_cents_annual, billing_cycle, currency, features, is_active, stripe_product_id, stripe_price_id_monthly, stripe_price_id_annual, stripe_metadata, sort_order, created_at, updated_at) VALUES ('plan_enterprise_annual', 'Enterprise Annual', '12% cheaper + unlimited + white-label + on-premise + 10% annual savings', 50000, 22900, 247320, 'annual', 'USD', '["All Professional features", "100 req/min, 6000/hour", "Unlimited concurrent requests", "Video capture (coming soon)", "Custom CSS injection (coming soon)", "Scheduled screenshots (coming soon)", "Dedicated support", "10% savings (2 months free)"]', true, 'prod_enterprise', 'price_enterprise_monthly', 'price_enterprise_annual', null, 4, '2025-06-14 04:29:16.816972', '2025-06-17 04:31:23.891211');

-- ============================================================================
-- STATISTICS TABLES FOR AGGREGATED USER ANALYTICS
-- ============================================================================

-- Daily User Statistics Table
create table public.daily_user_stats
(
    id                    varchar(255)                        not null
        primary key,
    user_id               varchar(255)                        not null
        references public.users
            on delete cascade,
    date                  date                                not null,
    screenshots_created   integer     default 0               not null,
    screenshots_completed integer     default 0               not null,
    screenshots_failed    integer     default 0               not null,
    screenshots_retried   integer     default 0               not null,
    credits_used          integer     default 0               not null,
    api_calls_count       integer     default 0               not null,
    api_keys_used         integer     default 0               not null,
    credits_added         integer     default 0               not null,
    payments_processed    integer     default 0               not null,
    api_keys_created      integer     default 0               not null,
    plan_changes          integer     default 0               not null,
    created_at            timestamp                           not null,
    updated_at            timestamp                           not null,
    version               bigint      default 1               not null
);

alter table public.daily_user_stats
    owner to screenshotuser;

-- Indexes for Daily User Stats
create unique index idx_daily_stats_user_date on public.daily_user_stats (user_id, date);
create index idx_daily_stats_user_date_range on public.daily_user_stats (user_id, date);
create index idx_daily_stats_date_activity on public.daily_user_stats (date, screenshots_created);
create index idx_daily_stats_user_month on public.daily_user_stats (user_id, date);
create index idx_daily_stats_date_retention on public.daily_user_stats (date);
create index idx_daily_stats_user_id on public.daily_user_stats (user_id);

-- Monthly User Statistics Table
create table public.monthly_user_stats
(
    id                      varchar(255)                        not null
        primary key,
    user_id                 varchar(255)                        not null
        references public.users
            on delete cascade,
    month                   varchar(7)                          not null, -- Format: "2025-01"
    screenshots_created     integer     default 0               not null,
    screenshots_completed   integer     default 0               not null,
    screenshots_failed      integer     default 0               not null,
    screenshots_retried     integer     default 0               not null,
    credits_used            integer     default 0               not null,
    api_calls_count         integer     default 0               not null,
    credits_added           integer     default 0               not null,
    peak_daily_screenshots  integer     default 0               not null,
    active_days             integer     default 0               not null,
    created_at              timestamp                           not null,
    updated_at              timestamp                           not null,
    version                 bigint      default 1               not null
);

alter table public.monthly_user_stats
    owner to screenshotuser;

-- Indexes for Monthly User Stats
create unique index idx_monthly_stats_user_month on public.monthly_user_stats (user_id, month);
create index idx_monthly_stats_month on public.monthly_user_stats (month);
create index idx_monthly_stats_user_year on public.monthly_user_stats (user_id, month);
create index idx_monthly_stats_user_id on public.monthly_user_stats (user_id);

-- Yearly User Statistics Table
create table public.yearly_user_stats
(
    id                        varchar(255)                        not null
        primary key,
    user_id                   varchar(255)                        not null
        references public.users
            on delete cascade,
    year                      integer                             not null,
    screenshots_created       integer     default 0               not null,
    screenshots_completed     integer     default 0               not null,
    screenshots_failed        integer     default 0               not null,
    screenshots_retried       integer     default 0               not null,
    credits_used              integer     default 0               not null,
    api_calls_count           integer     default 0               not null,
    credits_added             integer     default 0               not null,
    peak_monthly_screenshots  integer     default 0               not null,
    active_months             integer     default 0               not null,
    created_at                timestamp                           not null,
    updated_at                timestamp                           not null,
    version                   bigint      default 1               not null
);

alter table public.yearly_user_stats
    owner to screenshotuser;

-- Indexes for Yearly User Stats
create unique index idx_yearly_stats_user_year on public.yearly_user_stats (user_id, year);
create index idx_yearly_stats_year on public.yearly_user_stats (year);
create index idx_yearly_stats_user on public.yearly_user_stats (user_id);

-- ============================================================================
-- WEBHOOK SYSTEM TABLES
-- ============================================================================

-- Webhook Configurations Table
create table public.webhook_configurations
(
    id          varchar(50)                         not null
        primary key,
    user_id     varchar(50)                         not null
        constraint fk_webhook_configurations_user_id__id
            references public.users
            on update restrict on delete restrict,
    url         text                                not null,
    secret      varchar(255)                        not null,
    events      text                                not null, -- JSON array of event names
    is_active   boolean   default true              not null,
    description text,
    created_at  timestamp                           not null,
    updated_at  timestamp                           not null
);

alter table public.webhook_configurations
    owner to screenshotuser;

-- Indexes for Webhook Configurations
create index idx_webhook_configurations_user_id
    on public.webhook_configurations (user_id);
create index idx_webhook_configurations_is_active
    on public.webhook_configurations (is_active);
create index idx_webhook_configurations_user_active
    on public.webhook_configurations (user_id, is_active);

create trigger update_webhook_configurations_updated_at
    before update
    on public.webhook_configurations
    for each row
    execute procedure public.update_updated_at_column();

-- Webhook Deliveries Table
create table public.webhook_deliveries
(
    id                 varchar(50)                         not null
        primary key,
    webhook_config_id  varchar(50)                         not null
        constraint fk_webhook_deliveries_webhook_config_id__id
            references public.webhook_configurations
            on update restrict on delete restrict,
    user_id            varchar(50)                         not null
        constraint fk_webhook_deliveries_user_id__id
            references public.users
            on update restrict on delete restrict,
    event              varchar(50)                         not null,
    event_data         text                                not null, -- JSON
    payload            text                                not null, -- JSON payload sent
    signature          varchar(255)                        not null,
    status             varchar(20)                         not null,
    url                text                                not null,
    attempts           integer   default 1                 not null,
    max_attempts       integer   default 5                 not null,
    last_attempt_at    timestamp                           not null,
    next_retry_at      timestamp,
    response_code      integer,
    response_body      text,
    response_time_ms   bigint,
    error              text,
    created_at         timestamp                           not null
);

alter table public.webhook_deliveries
    owner to screenshotuser;

-- Indexes for Webhook Deliveries
create index idx_webhook_deliveries_webhook_config_id
    on public.webhook_deliveries (webhook_config_id);
create index idx_webhook_deliveries_user_id
    on public.webhook_deliveries (user_id);
create index idx_webhook_deliveries_status
    on public.webhook_deliveries (status);
create index idx_webhook_deliveries_status_retry
    on public.webhook_deliveries (status, next_retry_at)
    where status = 'RETRYING';
create index idx_webhook_deliveries_created_at
    on public.webhook_deliveries (created_at);
create index idx_webhook_deliveries_config_created
    on public.webhook_deliveries (webhook_config_id, created_at);

-- Comments for documentation
comment on table public.webhook_configurations
    is 'Stores user webhook configurations with HMAC secrets for secure payload verification';

comment on table public.webhook_deliveries
    is 'Tracks webhook delivery attempts with retry logic and comprehensive status tracking';

comment on column public.webhook_configurations.secret
    is 'HMAC secret for webhook payload verification - never exposed after creation';

comment on column public.webhook_deliveries.signature
    is 'HMAC-SHA256 signature of the payload for verification';

-- ==========================================
-- EMAIL LOGS TABLE
-- ==========================================

-- Table for tracking email delivery and engagement
create table public.email_logs (
    id                varchar(36)                                  not null
        primary key,
    user_id           varchar(36)                                  not null,
    email_type        varchar(50)                                  not null,
    subject           varchar(200)                                 not null,
    recipient_email   varchar(255)                                 not null,
    sent_at           timestamp                                    not null,
    opened            boolean     default false                    not null,
    clicked           boolean     default false                    not null,
    opened_at         timestamp,
    clicked_at        timestamp,
    bounced           boolean     default false                    not null,
    bounced_at        timestamp,
    unsubscribed      boolean     default false                    not null,
    unsubscribed_at   timestamp,
    metadata          text        default '{}'::text               not null,
    created_at        timestamp   default now()                    not null,
    updated_at        timestamp   default now()                    not null
);

alter table public.email_logs
    owner to screenshotuser;

-- Create indexes for performance
create index idx_email_logs_user_id on public.email_logs (user_id);
create index idx_email_logs_email_type on public.email_logs (email_type);
create index idx_email_logs_sent_at on public.email_logs (sent_at);
create index idx_email_logs_user_type on public.email_logs (user_id, email_type);
create index idx_email_logs_opened on public.email_logs (opened);
create index idx_email_logs_clicked on public.email_logs (clicked);
create index idx_email_logs_bounced on public.email_logs (bounced);
create index idx_email_logs_unsubscribed on public.email_logs (unsubscribed);

-- Create trigger for updated_at
create trigger update_email_logs_updated_at
    before update
    on public.email_logs
    for each row
    execute procedure public.update_updated_at_column();

-- Comments for documentation
comment on table public.email_logs
    is 'Tracks email delivery and engagement metrics for the email growth system';

comment on column public.email_logs.email_type
    is 'Type of email: WELCOME, CREDIT_ALERT_50, CREDIT_ALERT_80, CREDIT_ALERT_90, UPGRADE_CAMPAIGN, etc.';

comment on column public.email_logs.metadata
    is 'JSON metadata with email-specific data like template variables, provider info, etc.';

comment on column public.email_logs.opened
    is 'Whether the email was opened (tracked via pixel or other means)';

comment on column public.email_logs.clicked
    is 'Whether any link in the email was clicked';

-- ==========================================
-- OCR RESULTS TABLE
-- ==========================================

-- Table for storing OCR processing results
create table public.ocr_results (
    id                  varchar(255)                                 not null
        primary key,
    user_id             varchar(255)                                 not null
        constraint fk_ocr_results_user_id__id
            references public.users
            on update restrict on delete restrict,
    screenshot_job_id   varchar(255)
        constraint fk_ocr_results_screenshot_job_id__id
            references public.screenshots
            on update restrict on delete restrict,
    success             boolean                                      not null,
    extracted_text      text                                         not null,
    confidence          double precision                             not null,
    word_count          integer                                      not null,
    lines               text                                         not null, -- JSON serialized array
    processing_time     double precision                             not null,
    language            varchar(10)                                  not null,
    engine              varchar(50)                                  not null,
    structured_data     text,                                                  -- JSON serialized
    metadata            text,                                                  -- JSON serialized
    created_at          timestamp                                    not null,
    updated_at          timestamp                                    not null
);

alter table public.ocr_results
    owner to screenshotuser;

-- Performance indexes following codebase patterns
create index idx_ocr_results_user_id on public.ocr_results (user_id);
create index idx_ocr_results_screenshot_job_id on public.ocr_results (screenshot_job_id);
create index idx_ocr_results_created_at on public.ocr_results (created_at);

-- Create trigger for updated_at
create trigger update_ocr_results_updated_at
    before update
    on public.ocr_results
    for each row
    execute procedure public.update_updated_at_column();

-- Comments for documentation
comment on table public.ocr_results
    is 'Stores OCR processing results with extracted text and metadata';

comment on column public.ocr_results.lines
    is 'JSON array of OCR text lines with position and confidence data';

comment on column public.ocr_results.structured_data
    is 'JSON object containing structured data like tables, forms, prices if extracted';

comment on column public.ocr_results.metadata
    is 'JSON metadata with processing parameters, image info, tier settings, etc.';

-- ==========================================
-- USER FEEDBACK TABLE
-- ==========================================

-- Table for storing user feedback and insights
create table public.user_feedback (
    id                  varchar(255)                                 not null
        primary key,
    user_id             varchar(255)                                 not null
        constraint fk_user_feedback_user_id__id
            references public.users
            on update restrict on delete restrict,
    feedback_type       varchar(50)                                  not null,
    rating              integer,                                               -- 1-5 star rating, nullable
    subject             varchar(255),                                          -- Optional subject line
    message             text                                         not null, -- Main feedback content
    metadata            text                                         not null default '{}', -- JSON context
    status              varchar(50)                                  not null default 'PENDING',
    user_agent          text,                                                  -- Browser/client info
    ip_address          varchar(45),                                           -- For abuse prevention
    admin_notes         text,                                                  -- Internal notes
    resolved_by         varchar(255),                                          -- Admin who resolved
    resolved_at         timestamp,                                             -- Resolution timestamp
    created_at          timestamp                                    not null,
    updated_at          timestamp                                    not null
);

alter table public.user_feedback
    owner to screenshotuser;

-- Performance indexes for feedback queries
create index idx_user_feedback_user_id on public.user_feedback (user_id);
create index idx_user_feedback_type on public.user_feedback (feedback_type);
create index idx_user_feedback_status on public.user_feedback (status);
create index idx_user_feedback_created_at on public.user_feedback (created_at);
create index idx_user_feedback_rating on public.user_feedback (rating);
create index idx_user_feedback_resolved_at on public.user_feedback (resolved_at);

-- Composite indexes for common queries
create index idx_user_feedback_user_status on public.user_feedback (user_id, status);
create index idx_user_feedback_type_status on public.user_feedback (feedback_type, status);
create index idx_user_feedback_priority on public.user_feedback (feedback_type, rating, status);

-- Create trigger for updated_at
create trigger update_user_feedback_updated_at
    before update
    on public.user_feedback
    for each row
    execute procedure public.update_updated_at_column();

-- Comments for documentation
comment on table public.user_feedback
    is 'Stores user feedback and insights for product improvement and analytics';

comment on column public.user_feedback.feedback_type
    is 'Type of feedback: GENERAL, FEATURE_REQUEST, BUG_REPORT, SATISFACTION, CONVERSION_EXPERIENCE, etc.';

comment on column public.user_feedback.rating
    is '1-5 star rating for satisfaction feedback, nullable for non-rating feedback types';

comment on column public.user_feedback.metadata
    is 'JSON metadata with context like page visited, feature used, conversion stage, etc.';

comment on column public.user_feedback.status
    is 'Status: PENDING, REVIEWED, IN_PROGRESS, RESOLVED, CLOSED, ACKNOWLEDGED';

-- Check constraints for data integrity
alter table public.user_feedback
    add constraint chk_feedback_rating check (rating is null or (rating >= 1 and rating <= 5));

alter table public.user_feedback
    add constraint chk_feedback_type check (feedback_type in (
        'GENERAL', 'FEATURE_REQUEST', 'BUG_REPORT', 'SATISFACTION', 
        'CONVERSION_EXPERIENCE', 'UX_IMPROVEMENT', 'API_FEEDBACK', 'PERFORMANCE'
    ));

alter table public.user_feedback
    add constraint chk_feedback_status check (status in (
        'PENDING', 'REVIEWED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'ACKNOWLEDGED'
    ));


