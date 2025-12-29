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
                    
                    // Clean previous containers & volumes to avoid conflicts
                    sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" down -v || true'
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
                            
                            # Using 'docker compose' (space) for modern compatibility
                            docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d --wait
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
                    
                    // CORRECT IMAGE NAME BELOW
                    def zapImage = "zaproxy/zaproxy:stable"

                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh """
                        docker run --rm \
                            --network=${networkName} \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            ${zapImage} zap-api-scan.py \
                            -t ${TARGET_URL}/v3/api-docs \
                            -f openapi \
                            -r zap_api_report.html \
                            -J zap_api_report.json
                        """
                    }
                    
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh """
                        docker run --rm \
                            --network=${networkName} \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            ${zapImage} zap-full-scan.py \
                            -t ${TARGET_URL} \
                            -r zap_full_report.html \
                            -J zap_full_report.json \
                            -I
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

                // Teardown
                sh 'docker compose -p "${COMPOSE_PROJECT_NAME}" down -v'
                sh 'docker rmi "${IMAGE_NAME}" || true'
            }
        }
    }
}