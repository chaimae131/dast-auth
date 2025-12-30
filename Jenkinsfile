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

        stage('Debug Environment') {
            steps {
                script {
                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"
                    
                    echo "=== Checking Running Containers ==="
                    sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" ps'
                    
                    echo "=== Checking Networks ==="
                    sh "docker network ls | grep ${COMPOSE_PROJECT_NAME} || echo 'Network not found'"
                    
                    echo "=== Checking auth-service logs ==="
                    sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" logs auth-service || echo "No logs found"'
                    
                    echo "=== Testing connectivity from another container ==="
                    sh """
                        docker run --rm --network=${networkName} curlimages/curl:latest \
                            curl -v --max-time 10 http://auth-service:8080/actuator/health || echo "Connection failed"
                    """
                }
            }
        }
        
        stage('Verify Service is Up') {
            steps {
                script {
                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"
                    sh """
                        echo "=== Current App Logs ==="
                        docker logs auth-service --tail 20

                        echo "=== Starting Wait Loop ==="
                        # Use a standard while loop for better compatibility
                        i=1
                        while [ \$i -le 20 ]; do
                            STATUS=\$(docker run --rm --network=${networkName} curlimages/curl:latest curl -s -o /dev/null -w "%{http_code}" http://auth-service:8080/v3/api-docs || echo "000")
                            
                            if [ "\$STATUS" -eq 200 ] || [ "\$STATUS" -eq 401 ]; then
                                echo "✓ Service is up! (Status: \$STATUS)"
                                exit 0
                            fi

                            echo "Attempt \$i: Service returned \$STATUS. Waiting 5s..."
                            sleep 5
                            i=\$((i+1))
                        done

                        echo "✗ Timeout: Service did not start in time."
                        docker logs auth-service
                        exit 1
                    """
                }
            }
        }

        stage('Run DAST Scans - Unauthenticated') {
            steps {
                script {
                    def workspace = env.WORKSPACE
                    sh "rm -rf ${workspace}/zap-reports && mkdir -p ${workspace}/zap-reports && chmod 777 ${workspace}/zap-reports"

                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"
                    def zapImage   = "ghcr.io/zaproxy/zaproxy:stable"

                    // ================= API SCAN =================
                    echo "Starting ZAP API Scan..."
                    def apiScanStatus = sh(script: """
                        docker run --rm \
                            --network=${networkName} \
                            -v ${workspace}/zap-reports:/zap/wrk/:rw \
                            ${zapImage} zap-api-scan.py \
                            -t http://auth-service:8080/v3/api-docs \
                            -f openapi \
                            -r zap_api_report.html \
                            -J zap_api_report.json \
                            -I
                    """, returnStatus: true)
                    
                    echo "API Scan completed with status: ${apiScanStatus}"
                    
                    // Check if report was created
                    sh """
                        if [ -f "${workspace}/zap-reports/zap_api_report.html" ]; then
                            echo "✓ API report created successfully"
                            ls -lh ${workspace}/zap-reports/zap_api_report.html
                        else
                            echo "✗ API report NOT created!"
                        fi
                    """

                    // ================= FULL SCAN =================
                    echo "Starting ZAP Full Scan..."
                    def fullScanStatus = sh(script: """
                        docker run --rm \
                            --network=${networkName} \
                            -v ${workspace}/zap-reports:/zap/wrk/:rw \
                            ${zapImage} zap-full-scan.py \
                            -t http://auth-service:8080 \
                            -r zap_full_report.html \
                            -J zap_full_report.json \
                            -I
                    """, returnStatus: true)
                    
                    echo "Full Scan completed with status: ${fullScanStatus}"
                    
                    // Check if report was created
                    sh """
                        if [ -f "${workspace}/zap-reports/zap_full_report.html" ]; then
                            echo "✓ Full report created successfully"
                            ls -lh ${workspace}/zap-reports/zap_full_report.html
                        else
                            echo "✗ Full report NOT created!"
                        fi
                    """
                    
                    // List all files created
                    echo "All files in zap-reports:"
                    sh "ls -lah ${workspace}/zap-reports/"
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "=== Final Report Check ==="
                sh """
                    echo "Contents of zap-reports directory:"
                    ls -lah ${WORKSPACE}/zap-reports/ || echo "Directory not found"
                    
                    echo ""
                    echo "Checking for specific files:"
                    [ -f "${WORKSPACE}/zap-reports/zap_api_report.html" ] && echo "✓ API HTML exists" || echo "✗ API HTML missing"
                    [ -f "${WORKSPACE}/zap-reports/zap_api_report.json" ] && echo "✓ API JSON exists" || echo "✗ API JSON missing"
                    [ -f "${WORKSPACE}/zap-reports/zap_full_report.html" ] && echo "✓ Full HTML exists" || echo "✗ Full HTML missing"
                    [ -f "${WORKSPACE}/zap-reports/zap_full_report.json" ] && echo "✓ Full JSON exists" || echo "✗ Full JSON missing"
                """
                
                // Fix permissions
                sh "chmod -R 755 ${WORKSPACE}/zap-reports || true"
                
                // Archive artifacts - will succeed even if some files are missing
                archiveArtifacts artifacts: 'zap-reports/*', 
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