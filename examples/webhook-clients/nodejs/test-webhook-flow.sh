#!/bin/bash

# Webhook Flow Testing Script
# Comprehensive testing of webhook functionality with the Screenshot API

set -e  # Exit on any error

echo "🎯 Starting Webhook Flow Testing..."
echo "=================================="

# Configuration
API_BASE="http://localhost:8080"
WEBHOOK_SERVER="http://localhost:3001"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_test() {
    echo -e "\n${BLUE}🧪 Testing: $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

log_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Check if webhook test server is running
check_webhook_server() {
    log_test "Checking webhook test server"
    
    if curl -s "$WEBHOOK_SERVER/health" > /dev/null; then
        log_success "Webhook test server is running on $WEBHOOK_SERVER"
    else
        log_error "Webhook test server not responding at $WEBHOOK_SERVER"
        log_info "Please start the webhook server with: npm start"
        exit 1
    fi
}

# Check if main API is running
check_main_api() {
    log_test "Checking main Screenshot API"
    
    if curl -s "$API_BASE/health" > /dev/null; then
        log_success "Screenshot API is running on $API_BASE"
    else
        log_error "Screenshot API not responding at $API_BASE"
        log_info "Please start the main API server"
        exit 1
    fi
}

# Use provided token or get test token
setup_authentication() {
    if [ -n "$1" ]; then
        TEST_TOKEN="$1"
        log_success "Using provided token: ${TEST_TOKEN:0:20}..."
    else
        log_error "No token provided!"
        log_info "Usage: $0 <JWT_TOKEN>"
        log_info "Example: $0 eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        exit 1
    fi
}

# Test webhook events endpoint
test_webhook_events() {
    log_test "Getting available webhook events"
    
    local response=$(curl -s -X GET "$API_BASE/api/v1/webhooks/events" \
        -H "Authorization: Bearer $TEST_TOKEN")
    
    if echo "$response" | grep -q "SCREENSHOT_COMPLETED"; then
        log_success "Webhook events endpoint working"
        echo "Available events:"
        echo "$response" | jq '.events[].name' 2>/dev/null || echo "$response"
    else
        log_error "Webhook events endpoint failed"
        echo "Response: $response"
    fi
}

# Test create webhook
test_create_webhook() {
    log_test "Creating test webhook"
    
    local response=$(curl -s -X POST "$API_BASE/api/v1/webhooks" \
        -H "Authorization: Bearer $TEST_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{
            "url": "'$WEBHOOK_SERVER'/webhook",
            "events": ["SCREENSHOT_COMPLETED", "WEBHOOK_TEST"],
            "description": "Test webhook for flow testing"
        }')
    
    # Extract webhook ID from response
    WEBHOOK_ID=$(echo "$response" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$WEBHOOK_ID" ]; then
        log_success "Webhook created with ID: $WEBHOOK_ID"
        echo "Secret (first 20 chars): $(echo "$response" | grep -o '"secret":"[^"]*"' | cut -d'"' -f4 | head -c 20)..."
        
        # Save webhook secret for later use
        WEBHOOK_SECRET=$(echo "$response" | grep -o '"secret":"[^"]*"' | cut -d'"' -f4)
    else
        log_error "Failed to create webhook"
        echo "Response: $response"
    fi
}

# Test list webhooks
test_list_webhooks() {
    log_test "Listing user webhooks"
    
    local response=$(curl -s -X GET "$API_BASE/api/v1/webhooks" \
        -H "Authorization: Bearer $TEST_TOKEN")
    
    if echo "$response" | grep -q "webhooks"; then
        log_success "Webhooks listed successfully"
        local count=$(echo "$response" | grep -o '"total":[0-9]*' | cut -d':' -f2)
        echo "Total webhooks: $count"
    else
        log_error "Failed to list webhooks"
        echo "Response: $response"
    fi
}

# Test webhook manual trigger
test_webhook_trigger() {
    if [ -z "$WEBHOOK_ID" ]; then
        log_error "No webhook ID available for testing trigger"
        return
    fi
    
    log_test "Testing webhook manual trigger"
    
    # Set webhook secret in environment for test server
    export WEBHOOK_SECRET="$WEBHOOK_SECRET"
    
    local response=$(curl -s -X POST "$API_BASE/api/v1/webhooks/$WEBHOOK_ID/test" \
        -H "Authorization: Bearer $TEST_TOKEN")
    
    if echo "$response" | grep -q "id"; then
        log_success "Webhook test triggered"
        log_info "Check webhook test server at $WEBHOOK_SERVER for delivery"
        echo "Delivery ID: $(echo "$response" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)"
        
        # Wait a moment for delivery
        sleep 3
        
        # Check webhook server stats
        local stats=$(curl -s "$WEBHOOK_SERVER/stats")
        local total=$(echo "$stats" | grep -o '"total":[0-9]*' | cut -d':' -f2)
        echo "Total webhooks received by test server: $total"
    else
        log_error "Failed to trigger webhook test"
        echo "Response: $response"
    fi
}

# Test webhook delivery stats
test_webhook_deliveries() {
    if [ -z "$WEBHOOK_ID" ]; then
        log_error "No webhook ID available for testing deliveries"
        return
    fi
    
    log_test "Getting webhook deliveries"
    
    local response=$(curl -s -X GET "$API_BASE/api/v1/webhooks/$WEBHOOK_ID/deliveries" \
        -H "Authorization: Bearer $TEST_TOKEN")
    
    if echo "$response" | grep -q "deliveries"; then
        log_success "Webhook deliveries retrieved"
        local count=$(echo "$response" | grep -o '"total":[0-9]*' | cut -d':' -f2)
        echo "Total deliveries: $count"
    else
        log_error "Failed to get webhook deliveries"
        echo "Response: $response"
    fi
}

# Test webhook stats
test_webhook_stats() {
    if [ -z "$WEBHOOK_ID" ]; then
        log_error "No webhook ID available for testing stats"
        return
    fi
    
    log_test "Getting webhook delivery stats"
    
    local response=$(curl -s -X GET "$API_BASE/api/v1/webhooks/$WEBHOOK_ID/stats" \
        -H "Authorization: Bearer $TEST_TOKEN")
    
    if echo "$response" | grep -q "total"; then
        log_success "Webhook stats retrieved"
        echo "Stats: $response"
    else
        log_error "Failed to get webhook stats"
        echo "Response: $response"
    fi
}

# Test different webhook endpoints
test_different_endpoints() {
    log_test "Testing different webhook endpoints"
    
    # Test error endpoint
    log_info "Creating webhook for error endpoint"
    local error_response=$(curl -s -X POST "$API_BASE/api/v1/webhooks" \
        -H "Authorization: Bearer $TEST_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{
            "url": "'$WEBHOOK_SERVER'/webhook/test-error",
            "events": ["WEBHOOK_TEST"],
            "description": "Test webhook for error testing"
        }')
    
    local error_webhook_id=$(echo "$error_response" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$error_webhook_id" ]; then
        log_success "Error test webhook created: $error_webhook_id"
        
        # Trigger the error webhook
        curl -s -X POST "$API_BASE/api/v1/webhooks/$error_webhook_id/test" \
            -H "Authorization: Bearer $TEST_TOKEN" > /dev/null
        
        log_info "Error webhook triggered - should retry automatically"
        
        # Clean up error webhook
        curl -s -X DELETE "$API_BASE/api/v1/webhooks/$error_webhook_id" \
            -H "Authorization: Bearer $TEST_TOKEN" > /dev/null
    fi
}

# Test debug endpoint
test_debug_endpoint() {
    log_test "Testing debug endpoint"
    
    local response=$(curl -s -X GET "$API_BASE/api/v1/webhooks/debug/stats" \
        -H "Authorization: Bearer $TEST_TOKEN")
    
    if echo "$response" | grep -q "userId"; then
        log_success "Debug endpoint working"
        local webhook_count=$(echo "$response" | grep -o '"totalWebhooks":[0-9]*' | cut -d':' -f2)
        echo "Total webhooks for user: $webhook_count"
    else
        log_error "Debug endpoint failed"
        echo "Response: $response"
    fi
}

# Final verification
final_verification() {
    log_test "Final verification"
    
    # Check webhook server final stats
    local final_stats=$(curl -s "$WEBHOOK_SERVER/stats")
    local final_total=$(echo "$final_stats" | grep -o '"total":[0-9]*' | cut -d':' -f2)
    local final_valid=$(echo "$final_stats" | grep -o '"valid":[0-9]*' | cut -d':' -f2)
    
    log_success "Final webhook server stats:"
    echo "Total received: $final_total"
    echo "Valid signatures: $final_valid"
    
    # Show recent webhooks from server
    echo -e "\nRecent webhooks from server:"
    curl -s "$WEBHOOK_SERVER/webhooks?limit=5" | jq '.webhooks[] | {event: .headers.event, timestamp: .timestamp, valid: .valid}' 2>/dev/null || echo "jq not available for JSON formatting"
}

# Cleanup function
cleanup_webhook() {
    if [ -n "$WEBHOOK_ID" ]; then
        log_test "Cleaning up test webhook"
        curl -s -X DELETE "$API_BASE/api/v1/webhooks/$WEBHOOK_ID" \
            -H "Authorization: Bearer $TEST_TOKEN" > /dev/null
        log_success "Test webhook cleaned up"
    fi
}

# Main test execution
main() {
    echo "🚀 Starting comprehensive webhook flow tests..."
    
    # Pre-flight checks
    check_main_api
    check_webhook_server
    
    # Authentication
    setup_authentication "$1"
    
    # Basic API tests
    test_webhook_events
    test_create_webhook
    test_list_webhooks
    
    # Webhook flow tests
    test_webhook_trigger
    test_webhook_deliveries
    test_webhook_stats
    
    # Advanced tests
    test_different_endpoints
    test_debug_endpoint
    
    # Final verification
    final_verification
    
    # Cleanup
    cleanup_webhook
    
    echo -e "\n${GREEN}🎉 Webhook flow testing completed!${NC}"
    echo "=================================="
    echo "✅ All webhook functionality tested successfully"
    echo "🌐 Webhook test server: $WEBHOOK_SERVER"
    echo "📊 API documentation: $API_BASE/swagger"
    echo "🔍 Debug stats: $API_BASE/api/v1/webhooks/debug/stats"
}

# Trap to ensure cleanup on exit
trap cleanup_webhook EXIT

# Run main function
main "$@"