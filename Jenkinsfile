pipeline {
    agent any

    environment {
        // Define your image name
        IMAGE_NAME = "my-auth-service:${BUILD_NUMBER}"
        // Define the target URL for ZAP (Service name in docker-compose)
        TARGET_URL = "http://auth-service:8080" 
    }

    stages {
        stage('Build Docker Image') {
            steps {
                script {
                    // Build the image from the Dockerfile in root
                    sh "docker build -t ${IMAGE_NAME} ."
                }
            }
        }

        stage('Start Test Environment') {
            steps {
                // We wrap this in a script to use credentials
                script {
                    // Pull secrets from Jenkins Credentials Store
                    withCredentials([
                        string(credentialsId: 'auth-db-password', variable: 'DB_PASS'),
                        string(credentialsId: 'auth-jwt-secret', variable: 'JWT_SECRET')
                    ]) {
                        // Start the containers using the CI compose file
                        // We pass the env vars so docker-compose can substitute them
                        sh """
                            export IMAGE_NAME=${IMAGE_NAME}
                            export DB_PASS=${DB_PASS}
                            export JWT_SECRET=${JWT_SECRET}
                            docker-compose -f docker-compose.ci.yml up -d --wait
                        """
                    }
                }
            }
        }

        stage('Run OWASP ZAP DAST Scan') {
            steps {
                script {
                    // Create a directory for reports so Jenkins can read it later
                    sh 'mkdir -p zap-reports'
                    sh 'chmod 777 zap-reports'
                    
                    // Run ZAP container attached to the same network
                    // We use 'zap-full-scan.py' or 'zap-api-scan.py' depending on needs
                    // -t: target
                    // -r: report name
                    // --network: must match the docker-compose network name
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh """
                        docker run --rm \
                            --network=jenkins_ci-network \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            owasp/zap2docker-stable zap-api-scan.py \
                            -t ${TARGET_URL}/v3/api-docs \
                            -f openapi \
                            -r zap_report.html \
                            -J zap_json_report.json
                        """
                        // Note: If you don't have Swagger/OpenAPI available on that URL, 
                        // use 'zap-baseline.py -t ${TARGET_URL}' instead.
                    }
                }
            }
        }
    }

    post {
        always {
            // 1. Archive the artifacts (HTML Report)
            publishHTML(target: [
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'zap-reports',
                reportFiles: 'zap_report.html',
                reportName: 'OWASP ZAP Report',
                reportTitles: 'ZAP Security Scan'
            ])
            
            // 2. Tear down the environment
            script {
                sh "docker-compose -f docker-compose.ci.yml down -v"
                // Optional: Remove image to save space
                sh "docker rmi ${IMAGE_NAME}"
            }
        }
    }
}