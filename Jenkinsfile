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

                    // 1. Cleanup any old containers from failed runs
                    sh "docker rm -f zap_api_scan zap_full_scan || true"

                    // ================= API SCAN =================
                    echo "=== Starting ZAP API Scan ==="
                    // We remove -v and --rm. We use --name so we can find it.
                    sh """
                        docker run --name zap_api_scan \
                            --network=${networkName} \
                            ${zapImage} zap-api-scan.py \
                            -t http://auth-service:8080/v3/api-docs \
                            -f openapi \
                            -r zap_api_report.html \
                            -J zap_api_report.json \
                            -I || true
                    """

                    // Pull the files out of the container before it's deleted
                    echo "Extracting API reports..."
                    sh """
                        docker cp zap_api_scan:/zap/wrk/zap_api_report.html ${workspace}/zap-reports/ || echo "HTML missing"
                        docker cp zap_api_scan:/zap/wrk/zap_api_report.json ${workspace}/zap-reports/ || echo "JSON missing"
                        docker rm -f zap_api_scan
                    """

                    // ================= FULL SCAN =================
                    echo "=== Starting ZAP Full Scan ==="
                    sh """
                        docker run --name zap_full_scan \
                            --network=${networkName} \
                            ${zapImage} zap-full-scan.py \
                            -t http://auth-service:8080 \
                            -r zap_full_report.html \
                            -J zap_full_report.json \
                            -I || true
                    """

                    echo "Extracting Full reports..."
                    sh """
                        docker cp zap_full_scan:/zap/wrk/zap_full_report.html ${workspace}/zap-reports/ || echo "Full HTML missing"
                        docker cp zap_full_scan:/zap/wrk/zap_full_report.json ${workspace}/zap-reports/ || echo "Full JSON missing"
                        docker rm -f zap_full_scan
                    """

                    // ================= VERIFICATION =================
                    sh """
                        echo "Checking workspace for extracted files:"
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