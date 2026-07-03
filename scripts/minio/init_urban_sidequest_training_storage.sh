#!/bin/sh
set -eu

: "${MINIO_ENDPOINT:?MINIO_ENDPOINT is required}"
: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
: "${URBAN_MINIO_BUCKET:=urban-sidequest-training}"
: "${URBAN_MINIO_USER:=urban_sidequest}"
: "${URBAN_MINIO_PASSWORD:?URBAN_MINIO_PASSWORD is required}"
: "${URBAN_MINIO_POLICY:=urban-sidequest-training-rw}"

POLICY_FILE="/scripts/minio/urban-sidequest-training-policy.json"

mc alias set common-minio "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "common-minio/$URBAN_MINIO_BUCKET"

if ! mc admin user info common-minio "$URBAN_MINIO_USER" >/dev/null 2>&1; then
  mc admin user add common-minio "$URBAN_MINIO_USER" "$URBAN_MINIO_PASSWORD"
fi

if ! mc admin policy info common-minio "$URBAN_MINIO_POLICY" >/dev/null 2>&1; then
  mc admin policy create common-minio "$URBAN_MINIO_POLICY" "$POLICY_FILE"
fi

mc admin policy attach common-minio "$URBAN_MINIO_POLICY" --user "$URBAN_MINIO_USER"
mc ls "common-minio/$URBAN_MINIO_BUCKET"
