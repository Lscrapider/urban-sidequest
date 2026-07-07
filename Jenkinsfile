pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        COMPOSE_FILE_PATH = 'docker-compose.yml'
        ENV_FILE_PATH = '.env'
        DOCKER_NETWORK_NAME = 'docker-database-common-network'
        HEALTH_URL = 'http://127.0.0.1:8082/urban-api/api/health'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Write Env') {
            steps {
                withCredentials([file(credentialsId: 'urban-sidequest-prod-env-file', variable: 'PROD_ENV_FILE')]) {
                    sh '''
                        set +x
                        cp "$PROD_ENV_FILE" "$ENV_FILE_PATH"
                        tr -d '\r' < "$ENV_FILE_PATH" > "$ENV_FILE_PATH.normalized"
                        mv "$ENV_FILE_PATH.normalized" "$ENV_FILE_PATH"
                        chmod 600 "$ENV_FILE_PATH"
                    '''
                }
            }
        }

        stage('Validate Env') {
            steps {
                sh '''
                    set +x

                    if [ ! -s "$ENV_FILE_PATH" ]; then
                        echo "ERROR: $ENV_FILE_PATH is empty. Check Jenkins Secret File credential: urban-sidequest-prod-env-file"
                        exit 1
                    fi

                    line_count="$(wc -l < "$ENV_FILE_PATH" | tr -d ' ')"
                    if [ "$line_count" -lt 35 ]; then
                        echo "ERROR: $ENV_FILE_PATH has only $line_count lines. It should be the full multiline .env.github-secret content."
                        exit 1
                    fi

                    required_keys="
                    COMMON_POSTGRES_HOST
                    COMMON_POSTGRES_PORT
                    COMMON_POSTGRES_DB
                    COMMON_POSTGRES_USER
                    COMMON_POSTGRES_PASSWORD
                    POSTGRES_DB
                    POSTGRES_USER
                    POSTGRES_PASSWORD
                    COMMON_MINIO_ENDPOINT
                    COMMON_MINIO_ROOT_USER
                    COMMON_MINIO_ROOT_PASSWORD
                    URBAN_MINIO_USER
                    URBAN_MINIO_PASSWORD
                    SPRING_DATASOURCE_URL
                    SPRING_DATASOURCE_USERNAME
                    SPRING_DATASOURCE_PASSWORD
                    SPRING_REDIS_URL
                    SPRING_REDIS_PASSWORD
                    NEW_API_KEY
                    ROUTE_LLM_BASE_URL
                    AMAP_WEB_KEY
                    AMAP_WEB_KEYS
                    BAIDU_MAP_AK
                    ROUTE_PREF_MINIO_ENDPOINT
                    ROUTE_PREF_MINIO_ACCESS_KEY
                    ROUTE_PREF_MINIO_SECRET_KEY
                    ROUTE_SCORING_CONFIG_PATH
                    AUTH_JWT_SECRET
                    AUTH_DEV_VERIFICATION_CODE
                    URBAN_API_BASE_URL
                    URBAN_INTERNAL_BASE_URL
                    "

                    missing_keys=""
                    for key in $required_keys; do
                        if ! grep -Eq "^${key}=.+" "$ENV_FILE_PATH"; then
                            missing_keys="$missing_keys $key"
                        fi
                    done

                    if [ -n "$missing_keys" ]; then
                        echo "ERROR: Required env keys are missing or empty:$missing_keys"
                        echo "Fix Jenkins Secret File credential urban-sidequest-prod-env-file and upload the full .env.github-secret file."
                        exit 1
                    fi

                    echo "Env file validation passed: $line_count lines, required keys are present."
                '''
            }
        }

        stage('Validate Compose') {
            steps {
                sh '''
                    docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" config --quiet
                '''
            }
        }

        stage('Ensure Network') {
            steps {
                sh '''
                    docker network inspect "$DOCKER_NETWORK_NAME" >/dev/null 2>&1 \
                        || docker network create "$DOCKER_NETWORK_NAME"
                '''
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B -f backend/pom.xml test'
            }
        }

        stage('Build Images') {
            steps {
                sh '''
                    docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" build postgres-init urban-service
                '''
            }
        }

        stage('Init Dependencies') {
            steps {
                sh '''
                    docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" run --rm postgres-init
                    docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" run --rm minio-init
                '''
            }
        }

        stage('Start Service') {
            steps {
                sh '''
                    docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" up -d --force-recreate --remove-orphans urban-service
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    set -eu
                    for attempt in $(seq 1 30); do
                        if curl -fsS "$HEALTH_URL" >/dev/null; then
                            exit 0
                        fi
                        sleep 2
                    done
                    curl -fsS "$HEALTH_URL"
                '''
            }
        }

        stage('Status') {
            steps {
                sh '''
                    docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" ps
                '''
            }
        }
    }
}
