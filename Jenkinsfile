pipeline {
    agent any

    environment {
        IMAGE_NAME = "my-auth-service:${BUILD_NUMBER}"
        TARGET_URL = "http://auth-service:8080"
        COMPOSE_PROJECT_NAME = "jenkins-dast-scan" 
        COMPOSE_FILE = "docker-compose.yaml"
    }

    stages {
        stage('Build Image') {
            steps {
                sh 'docker build -t "${IMAGE_NAME}" .'
            }
        }

        
        stage('Start Test Environment') {
            steps {
                script {
                    sh "chmod -R +r ./init-db"
                    
                    // Clean previous containers & volumes
                    sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" down -v'

                    withCredentials([
                        string(credentialsId: 'discovery-image-id', variable: 'DISCOVERY_IMAGE'),
                        string(credentialsId: 'gateway-image-id', variable: 'GATEWAY_IMAGE'),
                        string(credentialsId: 'auth-db-password', variable: 'DB_PASS'),
                        string(credentialsId: 'auth-jwt-secret', variable: 'JWT_SECRET'),
                        string(credentialsId: 'email-ad', variable: 'EMAIL_AD'),
                        string(credentialsId: 'email-pass', variable: 'EMAIL_PASS')
                    ]) {
                        sh 'docker pull "${DISCOVERY_IMAGE}"'
                        sh 'docker pull "${GATEWAY_IMAGE}"'

                        sh '''
                            export IMAGE_NAME="${IMAGE_NAME}"
                            export DISCOVERY_IMAGE="${DISCOVERY_IMAGE}"
                            export GATEWAY_IMAGE="${GATEWAY_IMAGE}"
                            export DB_PASS="${DB_PASS}"
                            export JWT_SECRET="${JWT_SECRET}"
                            export EMAIL_AD="${EMAIL_AD}"
                            export EMAIL_PASS="${EMAIL_PASS}"

                            #  Start only Postgres + discovery + gateway
                            docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d postgres-db discovery-service gateway-service --wait

                            #  Ensure authdb exists
                            echo "Creating authdb if it does not exist..."
                            docker exec -i postgres-db psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='authdb'" | grep -q 1 || \
                            docker exec -i postgres-db psql -U postgres -c "CREATE DATABASE authdb;"

                            # Start auth-service after DB is ready
                            docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d auth-service --wait
                        '''
                    }
                }
            }
        }

        stage('Run DAST Scans') {
            steps {
                script {
                    sh 'mkdir -p zap-reports && chmod 777 zap-reports'

                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"
                    def zapImage   = "ghcr.io/zaproxy/zaproxy:stable"

                    // ---- Get JWT token (REQUIRED) ----
                    def token = sh(
                        script: """
                            curl -s -X POST http://auth-service:8080/api/auth/login \
                            -H 'Content-Type: application/json' \
                            -d '{"username":"testuser","password":"password"}' \
                            | jq -r .token
                        """,
                        returnStdout: true
                    ).trim()

                    // ================= API SCAN =================
                    echo "Starting ZAP API Scan..."
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh """
                        docker run --rm \
                            --network=${networkName} \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            ${zapImage} zap-api-scan.py \
                            -t http://auth-service:8080/v3/api-docs \
                            -f openapi \
                            -O http://auth-service:8080 \
                            -r zap_api_report.html \
                            -J zap_api_report.json \
                            -z "-config replacer.full_list(0).description=auth \
                                -config replacer.full_list(0).enabled=true \
                                -config replacer.full_list(0).matchtype=REQ_HEADER \
                                -config replacer.full_list(0).matchstr=Authorization \
                                -config replacer.full_list(0).regex=false \
                                -config replacer.full_list(0).replacement=Bearer\\ ${token}"
                        """
                    }

                    // ================= FULL SCAN =================
                    echo "Starting ZAP Full Scan..."
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh """
                        docker run --rm \
                            --network=${networkName} \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            ${zapImage} zap-full-scan.py \
                            -t http://auth-service:8080 \
                            -r zap_full_report.html \
                            -J zap_full_report.json \
                            -I \
                            -z "-config replacer.full_list(0).description=auth \
                                -config replacer.full_list(0).enabled=true \
                                -config replacer.full_list(0).matchtype=REQ_HEADER \
                                -config replacer.full_list(0).matchstr=Authorization \
                                -config replacer.full_list(0).regex=false \
                                -config replacer.full_list(0).replacement=Bearer\\ ${token}"
                        """
                    }
                }
            }
        }


    }
        
            
    post {
        always {
            script {
                // Ensure HTML Publisher plugin is installed
                publishHTML(target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'zap-reports',
                    reportFiles: 'zap_api_report.html',
                    reportName: 'ZAP API Report',
                    reportTitles: 'API Security Scan'
                ])
                
                publishHTML(target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'zap-reports',
                    reportFiles: 'zap_full_report.html',
                    reportName: 'ZAP Full Report',
                    reportTitles: 'Full DAST Scan'
                ])

                
            }
        }
    }
}