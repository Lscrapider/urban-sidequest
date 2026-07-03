# Route Preference MinIO Training Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move route preference preheat training data out of the product PostgreSQL database and into MinIO-backed training datasets.

**Architecture:** Spring Boot only publishes preheat training inputs to a MinIO `ingest/` queue using immutable JSON.GZ objects plus ready markers. Python consumes complete ingest objects, builds versioned `datasets/<dataset_version>/` artifacts with Parquet and manifest files, verifies the dataset, then deletes only the processed ingest objects. Python training reads from a manifest instead of PostgreSQL.

**Tech Stack:** Spring Boot 3.4, Java 17, MinIO Java SDK or S3-compatible SDK, Python, PyTorch, psycopg for one-time migration, pyarrow for Parquet, boto3 or minio Python SDK for MinIO.

---

## Constraints

- Product PostgreSQL must not store preheat training data, preheat manifests, object keys, candidate-set training lifecycle rows, or training indexes.
- Remove old PostgreSQL write path code for `route_preference_training_samples`, `route_preference_raw_snapshots`, and `route_preference_judgments`; do not keep dual-write compatibility.
- Treat `route_preference_candidate_sets` as related training lifecycle state. If implementation confirms it has no product purpose, drop it together with the training tables.
- Reuse the existing common Docker MinIO service. Do not add a project-specific MinIO server service or MinIO server container to this project.
- It is allowed to modify `docker-compose.yml` to add a short-lived `minio-init` container that runs MinIO Client (`mc`) commands against the existing common MinIO service, then exits.
- Initialize only Urban Sidequest-specific MinIO resources: bucket, scoped policy, and an `urban_sidequest` user or service account token.
- Do not commit, push, or create branches without explicit user approval.
- Do not create new unit tests without explicit user approval. Existing tests that become obsolete may be updated or removed as part of code cleanup.
- Do not delete PostgreSQL source data operationally until export verification succeeds and the user confirms destructive cleanup.

## File Map

### MinIO Initialization

- Create `scripts/minio/urban-sidequest-training-policy.json`
  - Scoped read/write policy for the route preference training bucket/prefix.
- Create `scripts/minio/init_urban_sidequest_training_storage.sh`
  - Initializes bucket, policy, and project credentials against the existing common MinIO service.
  - Uses MinIO Client (`mc`) against the existing common MinIO endpoint.
  - Does not start a MinIO server.
- Modify `docker-compose.yml`
  - Add a `minio-init` service using an `mc` client image.
  - The service depends on the existing common MinIO endpoint being reachable, runs the init script, then exits.
  - Do not add a project-specific `minio` server service.

### Spring Boot

- Modify `backend/pom.xml`
  - Add MinIO/S3 SDK dependency.
- Modify `backend/src/main/resources/application.yml`
  - Replace `route.preference.training.raw-snapshot-enabled` with object storage settings.
- Modify `backend/src/main/resources/application-dev.yml`
  - Add local MinIO defaults through environment placeholders.
- Create `backend/src/main/java/com/urbansidequest/backend/config/RoutePreferenceTrainingStorageProperties.java`
  - Holds endpoint, bucket, access key, secret key, secure flag, ingest prefix, and ready marker settings.
- Create `backend/src/main/java/com/urbansidequest/backend/config/TrainingObjectStorageConfig.java`
  - Builds the MinIO/S3 client bean.
- Create `backend/src/main/java/com/urbansidequest/backend/provider/route/training/RoutePreferenceTrainingObjectStore.java`
  - Owns object writing, gzip encoding, key construction, ready marker writes, and delete-free safety.
- Create `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceTrainingIngestPayload.java`
  - Candidate-set ingest payload containing raw snapshot payload and route feature snapshots.
- Create `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceJudgmentIngestPayload.java`
  - Judgment ingest payload containing current judgment API fields.
- Modify `backend/src/main/java/com/urbansidequest/backend/handler/route/step/SaveRoutePreferenceTrainingSamplesStep.java`
  - Replace PostgreSQL sample/raw writes with MinIO candidate-set ingest write.
  - Keep route history write because route history is product data.
  - Consider renaming to `PublishRoutePreferenceTrainingDataStep` if the resulting diff stays focused.
- Modify `backend/src/main/java/com/urbansidequest/backend/service/impl/RoutePreferenceTrainingServiceImpl.java`
  - Replace judgment table insert and sample mark-ready with MinIO judgment object write.
- Delete obsolete PG persistence classes after call sites are migrated:
  - `backend/src/main/java/com/urbansidequest/backend/domain/po/RoutePreferenceTrainingSamplePO.java`
  - `backend/src/main/java/com/urbansidequest/backend/domain/po/RoutePreferenceRawSnapshotPO.java`
  - `backend/src/main/java/com/urbansidequest/backend/domain/po/RoutePreferenceJudgmentPO.java`
  - `backend/src/main/java/com/urbansidequest/backend/mapper/RoutePreferenceTrainingSampleMapper.java`
  - `backend/src/main/java/com/urbansidequest/backend/mapper/RoutePreferenceRawSnapshotMapper.java`
  - `backend/src/main/java/com/urbansidequest/backend/mapper/RoutePreferenceJudgmentMapper.java`
  - `backend/src/main/java/com/urbansidequest/backend/manage/RoutePreferenceTrainingSampleManage.java`
  - `backend/src/main/java/com/urbansidequest/backend/manage/RoutePreferenceRawSnapshotManage.java`
  - `backend/src/main/java/com/urbansidequest/backend/manage/RoutePreferenceJudgmentManage.java`
  - `backend/src/main/java/com/urbansidequest/backend/service/RoutePreferenceFeatureRebuildService.java`
  - `backend/src/main/java/com/urbansidequest/backend/service/impl/RoutePreferenceFeatureRebuildServiceImpl.java`
  - `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotRestorer.java`
- Create `database/migrations/V14__drop_route_preference_training_tables.sql`
  - Drops training/preheat tables from product schema after export.

### Python

- Modify `ai-python/requirements.txt`
  - Add `pyarrow`.
  - Add MinIO client dependency, preferably `boto3` for S3-compatible access or `minio` for direct MinIO SDK.
- Create `ai-python/src/urban_sidequest_ai/models/route_preference/training/object_storage.py`
  - MinIO client config, list/read/write/delete object helpers, gzip JSON helpers.
- Create `ai-python/src/urban_sidequest_ai/models/route_preference/training/ingest_repository.py`
  - Reads Spring Boot ingest objects by ready marker.
- Create `ai-python/src/urban_sidequest_ai/models/route_preference/training/dataset_manifest.py`
  - Manifest dataclass and validation helpers.
- Create `ai-python/src/urban_sidequest_ai/models/route_preference/training/dataset_builder.py`
  - Converts ingest JSON.GZ into `training_samples.parquet`, `judgments.parquet`, `raw_snapshot_index.parquet`, copied raw snapshot objects, and `manifest.json`.
- Create `ai-python/src/urban_sidequest_ai/models/route_preference/training/dataset_repository.py`
  - Reads a manifest-backed dataset and returns existing `TrainingSampleRow` and `JudgmentRow` objects.
- Modify `ai-python/src/urban_sidequest_ai/models/route_preference/training/train.py`
  - Replace PostgreSQL repository loading with manifest-backed dataset loading.
- Keep `ai-python/src/urban_sidequest_ai/models/route_preference/training/repository.py`
  - Only for legacy PostgreSQL export/migration scripts, or rename to `pg_repository.py` during cleanup.
- Create `ai-python/src/urban_sidequest_ai/models/route_preference/training/export_pg_to_minio_dataset.py`
  - One-time migration from existing PostgreSQL training tables to a MinIO dataset version.
- Modify or retire Python route preference judge code under `ai-python/src/urban_sidequest_ai/route_preference_judge/`
  - It currently assumes Java API writes judgments to PostgreSQL. Keep the API contract if Spring now writes judgment objects to MinIO.

## Object Layout

Use one bucket in the existing common MinIO service, for example `urban-sidequest-training`.

```text
route-preference/
  ingest/
    candidate_sets/shard=xx/{candidate_set_id}.json.gz
    candidate_sets_ready/shard=xx/{candidate_set_id}.json
    judgments/shard=xx/{candidate_set_id}/{judgment_id}.json.gz

  datasets/
    {dataset_version}/
      manifest.json
      training_samples.parquet
      judgments.parquet
      raw_snapshot_index.parquet
      raw_snapshots/
        shard=xx/{candidate_set_id}.json.gz
```

Shard rule:

```text
shard = first two lowercase hex chars of candidate_set_id without hyphens
```

Candidate-set object shape:

```json
{
  "candidateSetId": "uuid",
  "requestId": "uuid",
  "userId": "uuid-or-null",
  "createdAt": "2026-07-03T00:00:00+08:00",
  "rawSnapshot": {
    "rawSchemaVersion": "v1",
    "generateParam": {},
    "selectedRoutes": []
  },
  "trainingSamples": [
    {
      "candidateSetId": "uuid",
      "routeCode": "A",
      "featureSchemaVersion": "v5",
      "stopMatrixJson": [],
      "segmentMatrixJson": [],
      "routeDerivedVectorJson": [],
      "contextCrossVectorJson": [],
      "intraSetVectorJson": [],
      "contextJson": {}
    }
  ]
}
```

Judgment object shape:

```json
{
  "judgmentId": "uuid",
  "candidateSetId": "uuid",
  "judgeType": "LLM_SIM_USER",
  "judgeModel": "model-name",
  "judgePromptVersion": "version",
  "rankingJson": [],
  "acceptedRouteCodesJson": [],
  "rejectedRouteCodesJson": [],
  "reasonCodesJson": {},
  "confidence": 0.8,
  "status": "COMPLETED",
  "completedAt": "2026-07-03T00:00:00+08:00"
}
```

Manifest shape:

```json
{
  "datasetVersion": "20260703_001",
  "candidateSetCount": 2560,
  "featureSchemaVersion": "v5",
  "rawSchemaVersion": "v1",
  "sampleCount": 12800,
  "judgmentCount": 7680,
  "createdAt": "2026-07-03T00:00:00+08:00",
  "files": {
    "trainingSamples": "training_samples.parquet",
    "judgments": "judgments.parquet",
    "rawSnapshotIndex": "raw_snapshot_index.parquet",
    "rawSnapshotsPrefix": "raw_snapshots/"
  }
}
```

## Task 0: Initialize Common MinIO Resources

**Files:**
- Modify: `docker-compose.yml`
- Create: `scripts/minio/urban-sidequest-training-policy.json`
- Create: `scripts/minio/init_urban_sidequest_training_storage.sh`

- [ ] Create a scoped MinIO policy for the project bucket/prefix. Policy actions should allow:
  - `s3:GetBucketLocation`
  - `s3:ListBucket`
  - `s3:GetObject`
  - `s3:PutObject`
  - `s3:DeleteObject`

- [ ] Scope resources to the project bucket:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::urban-sidequest-training"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::urban-sidequest-training/route-preference/*"
      ]
    }
  ]
}
```

- [ ] Write an init script that uses the existing common MinIO endpoint and admin credentials from environment variables:

```bash
MINIO_ENDPOINT=http://localhost:9000
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
URBAN_MINIO_BUCKET=urban-sidequest-training
URBAN_MINIO_USER=urban_sidequest
URBAN_MINIO_PASSWORD=<provided-outside-script>
URBAN_MINIO_POLICY=urban-sidequest-training-rw
```

- [ ] The script must run these logical operations with `mc`:

```bash
mc alias set common-minio "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "common-minio/$URBAN_MINIO_BUCKET"
mc admin user add common-minio "$URBAN_MINIO_USER" "$URBAN_MINIO_PASSWORD"
mc admin policy create common-minio "$URBAN_MINIO_POLICY" scripts/minio/urban-sidequest-training-policy.json
mc admin policy attach common-minio "$URBAN_MINIO_POLICY" --user "$URBAN_MINIO_USER"
```

- [ ] If the common MinIO deployment standard prefers service accounts instead of normal users, replace the user/password step with `mc admin user svcacct add` and record the generated access key and secret key outside git.
- [ ] Do not write real MinIO secrets into repository files.
- [ ] Add a `minio-init` service to `docker-compose.yml`. It should use a MinIO Client image, not a MinIO server image:

```yaml
  minio-init:
    image: minio/mc:latest
    environment:
      MINIO_ENDPOINT: ${COMMON_MINIO_ENDPOINT:-http://common-minio:9000}
      MINIO_ROOT_USER: ${COMMON_MINIO_ROOT_USER:-minioadmin}
      MINIO_ROOT_PASSWORD: ${COMMON_MINIO_ROOT_PASSWORD:-minioadmin}
      URBAN_MINIO_BUCKET: ${URBAN_MINIO_BUCKET:-urban-sidequest-training}
      URBAN_MINIO_USER: ${URBAN_MINIO_USER:-urban_sidequest}
      URBAN_MINIO_PASSWORD: ${URBAN_MINIO_PASSWORD}
      URBAN_MINIO_POLICY: ${URBAN_MINIO_POLICY:-urban-sidequest-training-rw}
    volumes:
      - ./scripts/minio:/scripts/minio:ro
    command: ["/bin/sh", "/scripts/minio/init_urban_sidequest_training_storage.sh"]
    networks:
      - minio-common-network
```

- [ ] Add only the external network needed to reach the existing common MinIO service:

```yaml
networks:
  minio-common-network:
    external: true
    name: ${COMMON_MINIO_NETWORK:-docker-minio-common-network}
```

- [ ] Do not add a project-specific `minio` server service to `docker-compose.yml`.
- [ ] Verification command:

```bash
mc ls common-minio/urban-sidequest-training
```

Expected: bucket exists and is accessible with the initialized project credentials.

## Task 1: Add Spring MinIO Configuration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/java/com/urbansidequest/backend/config/RoutePreferenceTrainingStorageProperties.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/config/TrainingObjectStorageConfig.java`

- [ ] Add the MinIO or S3 SDK dependency to `backend/pom.xml`.
- [ ] Add `route.preference.training.storage.*` properties:
  - `endpoint`
  - `bucket`
  - `access-key`
  - `secret-key`
  - `secure`
  - `prefix`
  - `write-enabled`
- [ ] Remove `route.preference.training.raw-snapshot-enabled`.
- [ ] Implement `RoutePreferenceTrainingStorageProperties` as a `@ConfigurationProperties(prefix = "route.preference.training.storage")` bean.
- [ ] Implement the MinIO/S3 client bean in `TrainingObjectStorageConfig`.
- [ ] Use the `urban_sidequest` MinIO user or service account credentials created in Task 0 through environment variables only.
- [ ] Do not add a project-specific MinIO server container configuration to this project.
- [ ] Verify configuration binding by running:

```bash
cd backend
mvn -DskipTests compile
```

Expected: compile succeeds or fails only on later tasks that have not yet migrated call sites.

## Task 2: Implement Spring Object Store Writer

**Files:**
- Create: `backend/src/main/java/com/urbansidequest/backend/provider/route/training/RoutePreferenceTrainingObjectStore.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceTrainingIngestPayload.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceJudgmentIngestPayload.java`

- [ ] Define candidate-set ingest payload as a Java record with raw snapshot payload and sample payload list.
- [ ] Define judgment ingest payload as a Java record matching the current judgment API fields plus generated `judgmentId`, `status`, and `completedAt`.
- [ ] Implement object keys:

```text
route-preference/ingest/candidate_sets/shard={shard}/{candidateSetId}.json.gz
route-preference/ingest/candidate_sets_ready/shard={shard}/{candidateSetId}.json
route-preference/ingest/judgments/shard={shard}/{candidateSetId}/{judgmentId}.json.gz
```

- [ ] Write candidate-set object first, then ready marker second.
- [ ] Write judgment objects independently.
- [ ] Do not implement delete in Spring Boot. Cleanup is Python dataset builder ownership.
- [ ] Fail the request if object storage write fails, so preheat data loss is visible.

## Task 3: Replace Spring PostgreSQL Training Writes

**Files:**
- Modify: `backend/src/main/java/com/urbansidequest/backend/handler/route/step/SaveRoutePreferenceTrainingSamplesStep.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/service/impl/RoutePreferenceTrainingServiceImpl.java`
- Modify if rename is chosen: `backend/src/main/java/com/urbansidequest/backend/handler/route/pipeline/RouteGenerationPipeline.java`

- [ ] In the route generation step, keep `routeGenerationHistoryManage.upsertHistory(...)`.
- [ ] Build raw snapshot payload with existing `RoutePreferenceRawSnapshotBuilder`.
- [ ] Build feature snapshots with existing `RouteInputFeatureExtractor.extractCandidateSet(context)`.
- [ ] Publish one candidate-set ingest object to MinIO.
- [ ] Remove dependencies on `RoutePreferenceTrainingSampleManage`, `RoutePreferenceRawSnapshotManage`, and `RoutePreferenceTrainingProperties.rawSnapshotEnabled`.
- [ ] In `RoutePreferenceTrainingServiceImpl.saveJudgment`, generate a `judgmentId`, serialize the request fields, write a judgment ingest object to MinIO, and return `RoutePreferenceJudgmentVO`.
- [ ] Remove `@Transactional` from judgment save if it only covered old PostgreSQL writes.
- [ ] Remove label payload and source weight methods if they no longer have call sites.

## Task 4: Delete Old Spring PostgreSQL Training Persistence

**Files:**
- Delete obsolete Java classes listed in the File Map.
- Modify imports and constructors in any remaining classes.
- Remove or update existing tests that directly assert old PostgreSQL training writes.

- [ ] Delete training sample/raw snapshot/judgment PO classes.
- [ ] Delete training sample/raw snapshot/judgment mapper classes.
- [ ] Delete training sample/raw snapshot/judgment manage classes.
- [ ] Delete PG raw snapshot rebuild service and restorer.
- [ ] Remove obsolete imports from route generation and training service classes.
- [ ] Run:

```bash
cd backend
mvn -DskipTests compile
```

Expected: compile succeeds.

## Task 5: Add Product DB Drop Migration

**Files:**
- Create: `database/migrations/V14__drop_route_preference_training_tables.sql`

- [ ] Add a migration that drops training/preheat tables from product DB:

```sql
DROP TABLE IF EXISTS route_preference_judgments;
DROP TABLE IF EXISTS route_preference_raw_snapshots;
DROP TABLE IF EXISTS route_preference_training_samples;
DROP TABLE IF EXISTS route_preference_candidate_sets;
```

- [ ] Confirm whether `route_preference_candidate_sets` has any product usage before keeping the drop line. Current evidence suggests it is training lifecycle state, not product data.
- [ ] Do not manually run destructive cleanup against a real database until export verification is complete and the user confirms.

## Task 6: Add Python MinIO Dataset Infrastructure

**Files:**
- Modify: `ai-python/requirements.txt`
- Create: `ai-python/src/urban_sidequest_ai/models/route_preference/training/object_storage.py`
- Create: `ai-python/src/urban_sidequest_ai/models/route_preference/training/dataset_manifest.py`
- Create: `ai-python/src/urban_sidequest_ai/models/route_preference/training/ingest_repository.py`

- [ ] Add dependencies:

```text
pyarrow>=15.0
boto3>=1.34
```

- [ ] Implement environment config:
  - `ROUTE_PREF_MINIO_ENDPOINT`
  - `ROUTE_PREF_MINIO_ACCESS_KEY`
  - `ROUTE_PREF_MINIO_SECRET_KEY`
  - `ROUTE_PREF_MINIO_BUCKET`
  - `ROUTE_PREF_MINIO_SECURE`
  - `ROUTE_PREF_MINIO_PREFIX`
  - `ROUTE_PREF_DATASET_VERSION`
- [ ] Implement gzip JSON read/write helpers.
- [ ] Implement list/read/write/delete object helpers.
- [ ] Implement manifest model with validation:
  - required files are present
  - counts are positive
  - `featureSchemaVersion` matches the dataset rows
  - `rawSchemaVersion` is single-valued for the dataset

## Task 7: Build Dataset From Ingest and Clean Processed Objects

**Files:**
- Create: `ai-python/src/urban_sidequest_ai/models/route_preference/training/dataset_builder.py`

- [ ] List ready marker objects under `ingest/candidate_sets_ready/`.
- [ ] For each ready candidate set:
  - read candidate-set JSON.GZ
  - read judgment JSON.GZ objects under the same candidate set prefix
  - skip candidate sets with no completed judgments unless the command explicitly allows unlabeled export
- [ ] Write raw snapshot copies into `datasets/{dataset_version}/raw_snapshots/`.
- [ ] Write `training_samples.parquet`.
- [ ] Write `judgments.parquet`.
- [ ] Write `raw_snapshot_index.parquet`.
- [ ] Validate:
  - candidate set count matches processed ready set count
  - sample count matches the row count written to Parquet
  - judgment count matches the row count written to Parquet
  - feature schema version is single-valued unless explicitly overridden
- [ ] Write `manifest.json` last.
- [ ] After manifest write and validation, delete only processed ingest objects:
  - candidate-set JSON.GZ
  - candidate-set ready marker
  - judgment JSON.GZ objects for processed candidate sets
- [ ] Do not delete unprocessed or newly arrived ingest objects.

## Task 8: Replace Python Training Read Entry

**Files:**
- Create: `ai-python/src/urban_sidequest_ai/models/route_preference/training/dataset_repository.py`
- Modify: `ai-python/src/urban_sidequest_ai/models/route_preference/training/train.py`

- [ ] Implement `RoutePreferenceDatasetRepository` that reads the manifest and Parquet files.
- [ ] Convert Parquet rows into existing `TrainingSampleRow` and `JudgmentRow` dataclasses.
- [ ] Replace `load_database_config()`, `connect(...)`, and `RoutePreferenceTrainingRepository(...)` in `run_train`.
- [ ] Keep `build_dataset_bundle(...)` unchanged if row objects stay compatible.
- [ ] Keep `run_self_check(...)` unchanged.
- [ ] Run:

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.models.route_preference.training --help
```

Expected: module imports without PostgreSQL dependency for training data loading. If the module has no `--help`, run the existing self-check mode after implementation.

## Task 9: Add PostgreSQL To MinIO Migration Script

**Files:**
- Create: `ai-python/src/urban_sidequest_ai/models/route_preference/training/export_pg_to_minio_dataset.py`

- [ ] Reuse existing PostgreSQL `db.py` config for reading old tables.
- [ ] Read:
  - `route_preference_raw_snapshots`
  - `route_preference_training_samples`
  - `route_preference_judgments`
- [ ] Build one final dataset version directly under `datasets/{dataset_version}/`.
- [ ] Copy raw snapshots to JSON.GZ files.
- [ ] Write training samples and judgments to Parquet.
- [ ] Write raw snapshot index.
- [ ] Write manifest last.
- [ ] Print verification summary:

```text
candidateSetCount=2560
sampleCount=<count>
judgmentCount=<count>
featureSchemaVersions=<values>
rawSchemaVersions=<values>
manifest=s3://bucket/route-preference/datasets/<version>/manifest.json
```

- [ ] Do not delete PostgreSQL data in this script.

## Task 10: Update Existing Tests and Verification

**Files:**
- Modify or delete obsolete existing tests under:
  - `backend/src/test/java/com/urbansidequest/backend/handler/route/step/SaveRoutePreferenceTrainingSamplesStepTest.java`
  - `backend/src/test/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotRebuildTest.java`
  - `backend/src/test/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotFullChainManualTest.java`
  - `backend/src/test/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotRepairManualTest.java`

- [ ] Remove tests whose only purpose is PG raw snapshot/sample persistence or PG feature rebuild.
- [ ] Update existing route generation step tests only if they already exist and fail after constructor/call-site changes.
- [ ] Do not create new unit tests unless the user approves.
- [ ] Run:

```bash
cd backend
mvn test
```

Expected: existing non-obsolete tests pass.

- [ ] Run Python dataset import/self-check:

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.models.route_preference.training
```

Expected: training loads from manifest when configured, or self-check mode still passes when selected.

## Execution Notes

- Implement Spring and Python in separate passes; do not delete old PG classes until Spring compile points to MinIO writers.
- The migration script should be run before the DB drop migration is applied to any environment with valuable historical training data.
- Ingest cleanup must be idempotent: rerunning the dataset builder after a successful cleanup should not delete unrelated new objects.
- If MinIO dependency download fails during implementation because of network sandboxing, request escalation for the dependency command instead of working around it.

## Self-Review

- Spec coverage: The plan covers Spring write entry, Python train read entry, dataset builder, ingest cleanup, PostgreSQL export, and product DB table removal.
- Placeholder scan: No task relies on unresolved values; environment variable names, object paths, and file responsibilities are explicit.
- Type consistency: Existing `TrainingSampleRow` and `JudgmentRow` remain the Python compatibility boundary; Spring payload names are separated from old PO/Mapper names.
