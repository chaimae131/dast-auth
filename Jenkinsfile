pipeline {
    agent any

    tools {
        jdk 'JDK21'
    }

    environment {
        // --- CONFIGURATION GENERALE ---
        DOCKER_USER      = 'khadijaelghozail'
        REPO_NAME        = 'auth-service'
        IMAGE_NAME       = "${DOCKER_USER}/${REPO_NAME}"
        IMAGE_TAG        = "${BUILD_NUMBER}"
        
        // --- CONFIGURATION GITOPS ---
        GITOPS_REPO_URL  = 'github.com/KhadijaElghozail/Deployment-charts.git'
        CHART_FILE_PATH  = 'templates/microservices/auth-service.yaml'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                echo 'Cleaning workspace...'
                deleteDir()
            }
        }
        
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Verify Structure') {
            steps {
                sh '''
                    if [ -d "user-service" ] && [ -f "user-service/pom.xml" ]; then
                        echo "✓ Project structure is valid"
                    else
                        echo "✗ user-service/pom.xml not found"
                        exit 1
                    fi
                '''
            }
        }
        
        stage('Prepare') {
            steps {
                dir('user-service') {
                    sh '''
                        if [ -f mvnw ]; then 
                            chmod +x mvnw
                            cp mvnw /tmp/mvnw
                        fi
                    '''
                }
            }
        }
        
        stage('Secrets Scan (Gitleaks)') {
            steps {
                // Détecte les secrets, ne bloque pas le build si erreur (|| true) mais génère un rapport
                sh "gitleaks detect --source . --log-opts='HEAD~1..HEAD' --redact -r gitleaks-report.json -f json || true"
            }
        }

        stage('Build & Compile') {
            steps {
                dir('user-service') {
                    script {
                        def mvnCmd = fileExists('/tmp/mvnw') ? '/tmp/mvnw' : 'mvn'
                        sh "${mvnCmd} clean compile -DskipTests"
                    }
                }
            }
        }

        stage('Unit Tests & Coverage') {
            steps {
                dir('user-service') {
                    script {
                        def mvnCmd = fileExists('/tmp/mvnw') ? '/tmp/mvnw' : 'mvn'
                        // Exécute les tests JUnit + Génère le rapport JaCoCo
                        sh "${mvnCmd} test"
                    }
                }
            }
            post {
                always {
                    junit 'user-service/target/surefire-reports/*.xml'
                    jacoco(
                        execPattern: 'user-service/target/jacoco.exec',
                        classPattern: 'user-service/target/classes',
                        sourcePattern: 'user-service/src/main/java',
                        exclusionPattern: 'src/test*'
                    )
                }
            }
        }

        stage('Code Quality (SonarQube)') {
            steps {
                dir('user-service') {
                    script {
                        def mvnCmd = fileExists('/tmp/mvnw') ? '/tmp/mvnw' : 'mvn'
                        withSonarQubeEnv('SonarQube') {
                            sh """
                                ${mvnCmd} org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                -Dsonar.projectKey=user-service \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                            """
                        }
                    }
                }
            }
        }

        stage('SAST (SpotBugs)') {
            steps {
                dir('user-service') {
                    script {
                        def mvnCmd = fileExists('/tmp/mvnw') ? '/tmp/mvnw' : 'mvn'
                        sh "${mvnCmd} spotbugs:spotbugs"
                    }
                }
            }
            post {
                always {
                    recordIssues tools: [spotBugs(pattern: 'user-service/target/spotbugsXml.xml')]
                }
            }
        }

        stage('Dependency Scan (OWASP)') {
            steps {
                dir('user-service') {
                    script {
                        def mvnCmd = fileExists('/tmp/mvnw') ? '/tmp/mvnw' : 'mvn'
                        try {
                            withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                                sh "${mvnCmd} org.owasp:dependency-check-maven:check -Dnvd.api.key=\$NVD_API_KEY -Dformat=XML -DfailOnError=false"
                            }
                        } catch (Exception e) {
                            echo "OWASP Scan failed or NVD API unreachable: ${e.message}"
                        }
                    }
                }
            }
            post {
                always {
                    dependencyCheckPublisher pattern: 'user-service/target/dependency-check-report.xml'
                }
            }
        }
        
        stage('Dockerfile Lint (Hadolint)') {
            steps {
                script {
                    sh "docker run --rm -i hadolint/hadolint < Dockerfile | tee hadolint-report.txt || true"
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'hadolint-report.txt', allowEmptyArchive: true
                }
            }
        }

           stage('Docker Build') {
            steps {
                script {
                    docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                }
            }
        }

        stage('Container Security (Trivy)') {
            steps {
                sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image --severity HIGH,CRITICAL ${IMAGE_NAME}:${IMAGE_TAG} > trivy-report.txt || true"
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.txt', allowEmptyArchive: true
                }
            }
        }


         stage('Docker Push') {
            steps {
                script {
                    docker.withRegistry('https://index.docker.io/v1/', 'dockerhub-creds') {
                        def customImage = docker.image("${IMAGE_NAME}:${IMAGE_TAG}")
                        customImage.push()
                        customImage.push('latest')
                    }
                }
            }
        }

        
        stage('Update GitOps Manifest') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'helm-repo-crendentials', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                        sh """
                            # 1. Nettoyer tout dossier existant pour éviter les conflits
                            rm -rf config-repo

                            # 2. Cloner le repo (Correction du double https et du nom d'hôte)
                            # On utilise \$GIT_TOKEN pour que Jenkins masque correctement le secret
                            git clone https://${GIT_USER}:\$GIT_TOKEN@github.com/KhadijaElghozail/Deployment-charts.git config-repo
                            
                            cd config-repo
                            
                            # 3. Mise à jour du tag d'image dans le YAML
                            sed -i "s|image: .*khadijaelghozail/auth-service:.*|image: ${IMAGE_NAME}:${IMAGE_TAG}|g" ${CHART_FILE_PATH}
                            
                            # 4. Configuration Git et Push
                            git config user.email "jenkins@atlassplay.com"
                            git config user.name "Jenkins CI"
                            
                            # On vérifie s'il y a des changements avant de commit pour éviter l'erreur "nothing to commit"
                            if git diff --quiet ${CHART_FILE_PATH}; then
                                echo "Aucun changement détecté dans le manifest."
                            else
                                git add ${CHART_FILE_PATH}
                                git commit -m "chore: update auth-service image to version ${IMAGE_TAG}"
                                git push origin main
                            fi
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline terminé avec succès ! Image ${IMAGE_NAME}:${IMAGE_TAG} déployée via GitOps."
        }
        failure {
            echo "❌ Échec du pipeline. Vérifiez les logs."
        }
    }
}
