pipeline {
    agent any

    environment {
        // Built dynamically
        IMAGE_NAME = "my-auth-service:${BUILD_NUMBER}"
        
        // These 3 variables are defined in 'Manage Jenkins > System > Global Properties'
        // They are NOT in GitHub.
        // DISCOVERY_IMAGE, GATEWAY_IMAGE, REGISTRY_URL
        
        TARGET_URL = "http://auth-service:8080"
        COMPOSE_PROJECT_NAME = "jenkins-dast-scan" 
        COMPOSE_FILE = "docker-compose.yaml"
    }

    stages {
        stage('Build & Registry Login') {
            steps {
                script {
                    // Build local service
                    sh "docker build -t ${IMAGE_NAME} ."
                    
                    // Use a Credentials ID for the registry login
                    // 'my-registry-creds' is stored in Jenkins Credentials
                    withCredentials([usernamePassword(credentialsId: 'my-registry-creds', 
                                     usernameVariable: 'REG_USER', 
                                     passwordVariable: 'REG_PASS')]) {
                        sh "echo ${REG_PASS} | docker login ${REGISTRY_URL} -u ${REG_USER} --password-stdin"
                        sh "docker pull ${DISCOVERY_IMAGE}"
                        sh "docker pull ${GATEWAY_IMAGE}"
                    }
                }
            }
        }

        stage('Start Test Environment') {
            steps {
                script {
                    withCredentials([
                        usernamePassword(credentialsId: 'my-registry-creds', usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS'),
                        string(credentialsId: 'registry-url-id', variable: 'REGISTRY_URL'),
                        string(credentialsId: 'discovery-image-id', variable: 'DISCOVERY_IMAGE'),
                        string(credentialsId: 'gateway-image-id', variable: 'GATEWAY_IMAGE'),
                        string(credentialsId: 'auth-db-password', variable: 'DB_PASS'),
                        string(credentialsId: 'auth-jwt-secret', variable: 'JWT_SECRET')
                    ]) {
                        sh """
                            echo ${REG_PASS} | docker login ${REGISTRY_URL} -u ${REG_USER} --password-stdin
                            
                            export IMAGE_NAME=${IMAGE_NAME}
                            export DISCOVERY_IMAGE=${DISCOVERY_IMAGE}
                            export GATEWAY_IMAGE=${GATEWAY_IMAGE}
                            export DB_PASS=${DB_PASS}
                            export JWT_SECRET=${JWT_SECRET}
                            
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
                            -z "-config api.disable128=true" 
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
                sh "docker-compose -p ${COMPOSE_PROJECT_NAME} -f ${COMPOSE_FILE} down -v"
                sh "docker logout ${REGISTRY_URL}"
                sh "docker rmi ${IMAGE_NAME} || true"
            }
        }
    }
}