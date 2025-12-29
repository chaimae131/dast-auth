pipeline {
    agent any

    environment {
        IMAGE_NAME = "my-auth-service:${BUILD_NUMBER}"
        TARGET_URL = "http://auth-service:8080"
        COMPOSE_PROJECT_NAME = "jenkins-dast-scan" 
        COMPOSE_FILE = "docker-compose.yaml"
        // REGISTRY_URL is still used in the post-clean up if needed, 
        // or can be removed if login isn't required at all.
    }

    stages {
        stage('Build Image') {
            steps {
                // We only build the local service here
                sh "docker build -t ${IMAGE_NAME} ."
            }
        }

        stage('Start Test Environment') {
            steps {
                script {
                    // Ensure init-db scripts are readable
                    sh "chmod -R +r ./init-db"
                    
                    withCredentials([
                        string(credentialsId: 'discovery-image-id', variable: 'DISCOVERY_IMAGE'),
                        string(credentialsId: 'gateway-image-id', variable: 'GATEWAY_IMAGE'),
                        string(credentialsId: 'auth-db-password', variable: 'DB_PASS'),
                        string(credentialsId: 'auth-jwt-secret', variable: 'JWT_SECRET'),
                        string(credentialsId: 'email-ad', variable: 'EMAIL_AD'),
                        string(credentialsId: 'email-pass', variable: 'EMAIL_PASS')
                    ]) {
                        // We pull the images without explicit login (assuming server is pre-authenticated)
                        sh "docker pull ${DISCOVERY_IMAGE}"
                        sh "docker pull ${GATEWAY_IMAGE}"

                        sh """
                            export IMAGE_NAME=${IMAGE_NAME}
                            export DISCOVERY_IMAGE=${DISCOVERY_IMAGE}
                            export GATEWAY_IMAGE=${GATEWAY_IMAGE}
                            export DB_PASS=${DB_PASS}
                            export JWT_SECRET=${JWT_SECRET}
                            export EMAIL_AD=${EMAIL_AD}
                            export EMAIL_PASS=${EMAIL_PASS}
                            
                            docker-compose -p ${COMPOSE_PROJECT_NAME} -f ${COMPOSE_FILE} up -d --wait
                        """
                    }
                }
            }
        }

        stage('Run OWASP ZAP DAST Scan') {
            steps {
                script {
                    sh 'mkdir -p zap-reports && chmod 777 zap-reports'
                    def networkName = "${COMPOSE_PROJECT_NAME}_ci-network"

                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh """
                        docker run --rm \
                            --network=${networkName} \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            owasp/zap2docker-stable zap-api-scan.py \
                            -t ${TARGET_URL}/v3/api-docs \
                            -f openapi \
                            -r zap_report.html \
                            -J zap_json_report.json \
                            -z "-config api.disablekey=false" \
                            -d
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            publishHTML(target: [
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'zap-reports',
                reportFiles: 'zap_report.html',
                reportName: 'OWASP ZAP Report',
                reportTitles: 'ZAP Security Scan'
            ])
            
            script {
                // Clean up containers and volumes
                sh "docker-compose -p ${COMPOSE_PROJECT_NAME} -f ${COMPOSE_FILE} down -v"
                // Remove the locally built image to save space
                sh "docker rmi ${IMAGE_NAME} || true"
            }
        }
    }
}