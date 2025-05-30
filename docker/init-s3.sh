#!/bin/bash

# Wait for LocalStack to be ready
echo "Waiting for LocalStack to be ready..."
while ! curl -f http://localhost:4566/_localstack/health > /dev/null 2>&1; do
    echo "Waiting for LocalStack..."
    sleep 2
done

echo "LocalStack is ready. Creating S3 bucket..."

# Create S3 bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://screenshot-api-bucket

echo "S3 bucket 'screenshot-api-bucket' created successfully!"