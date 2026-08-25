#!/bin/sh
set -e
mc alias set local http://minio:9000 minioadmin minioadmin
mc mb --ignore-existing local/staging-raw
mc mb --ignore-existing local/reports-watermarked
mc anonymous set private local/staging-raw
mc anonymous set private local/reports-watermarked
echo "MinIO buckets initialized."
