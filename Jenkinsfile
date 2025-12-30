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

                            # Start only Postgres + discovery + gateway
                            docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d postgres-db discovery-service gateway-service --wait

                            # Ensure authdb exists
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

        stage('Wait for Service Ready') {
            steps {
                script {
                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"
                    
                    echo "=== Checking container status ==="
                    sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" ps'
                    
                    echo "=== Checking auth-service logs ==="
                    sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" logs --tail=30 auth-service'
                    
                    echo "=== Waiting for auth-service to respond ==="
                    sh """
                        # Wait up to 2 minutes for service
                        for i in \$(seq 1 24); do
                            echo "Attempt \$i/24: Testing service..."
                            
                            # Test the actual endpoint we'll scan
                            if docker run --rm --network=${networkName} curlimages/curl:latest \
                                curl -s --max-time 5 http://auth-service:8080/v3/api-docs > /dev/null 2>&1; then
                                echo "✓ Service is responding!"
                                
                                # Verify it's actually working
                                docker run --rm --network=${networkName} curlimages/curl:latest \
                                    curl -s http://auth-service:8080/v3/api-docs | head -20
                                exit 0
                            fi
                            
                            sleep 5
                        done
                        
                        echo "✗ Service failed to respond in time!"
                        echo "=== Final logs ==="
                        docker compose -p "${COMPOSE_PROJECT_NAME}" logs auth-service
                        exit 1
                    """
                }
            }
        }

    
        stage('Run DAST Scans - Unauthenticated') {
            steps {
                script {
                    def workspace = env.WORKSPACE
                    sh "mkdir -p ${workspace}/zap-reports"

                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"
                    def zapImage = "ghcr.io/zaproxy/zaproxy:stable"

                    // ================= API SCAN =================
                    echo "=== Starting ZAP API Scan (Manual Extraction) ==="
                    
                    // 1. Run ZAP without a volume. We name it so we can find it later.
                    // We do NOT use --rm yet.
                    sh(script: """
                        docker run --name zap_api_scan_container \
                            --network=${networkName} \
                            ${zapImage} zap-api-scan.py \
                            -t http://auth-service:8080/v3/api-docs \
                            -f openapi \
                            -r zap_api_report.html \
                            -J zap_api_report.json \
                            -I || true
                    """)

                    // 2. Extract the reports from the container's internal storage
                    echo "Extracting reports from container..."
                    sh """
                        docker cp zap_api_scan_container:/zap/wrk/zap_api_report.html ${workspace}/zap-reports/ || echo "HTML not found"
                        docker cp zap_api_scan_container:/zap/wrk/zap_api_report.json ${workspace}/zap-reports/ || echo "JSON not found"
                        
                        # 3. Cleanup: Now we can delete the container
                        docker rm zap_api_scan_container
                    """

                    // ================= VERIFICATION =================
                    sh """
                        echo "Final Workspace Check:"
                        ls -lah ${workspace}/zap-reports/
                    """
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "=== Final Report Summary ==="
                sh """
                    ls -lah ${WORKSPACE}/zap-reports/ || echo "Directory not found"
                    
                    echo ""
                    echo "Report Status:"
                    [ -f "${WORKSPACE}/zap-reports/zap_api_report.html" ] && echo "✓ API HTML" || echo "✗ API HTML"
                    [ -f "${WORKSPACE}/zap-reports/zap_api_report.json" ] && echo "✓ API JSON" || echo "✗ API JSON"
                    [ -f "${WORKSPACE}/zap-reports/zap_full_report.html" ] && echo "✓ Full HTML" || echo "✗ Full HTML"
                    [ -f "${WORKSPACE}/zap-reports/zap_full_report.json" ] && echo "✓ Full JSON" || echo "✗ Full JSON"
                """
                
                // Fix permissions
                sh "chmod -R 755 ${WORKSPACE}/zap-reports || true"
                
                // Archive all files in zap-reports
                archiveArtifacts artifacts: 'zap-reports/**/*', 
                                allowEmptyArchive: true,
                                fingerprint: true
                
                // Publish HTML reports
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'zap-reports',
                    reportFiles: 'zap_api_report.html',
                    reportName: 'ZAP API Report'
                ])
                
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'zap-reports',
                    reportFiles: 'zap_full_report.html',
                    reportName: 'ZAP Full Report'
                ])
            }
        }
        
        
        
    }
}