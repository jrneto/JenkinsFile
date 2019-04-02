#!/usr/bin/env groovy

pipeline {
    agent {
        label 'master'
    }

    parameters {
        string( name: 'BRANCH_NAME',
                description: 'Nome da branch que ser&#225; executada a build da aplica&#231;&#227;o. Ex.: RB_GEVO-1234',
                defaultValue: '',
                trim: true)
    }

    environment{
        solution_name = 'SACI.sln'
        assembly_name_hml = 'SACI - Homologacao'
        assembly_name_prd = 'SACI - Sistema Atendimento ao Cliente Interm\u00E9dica - v.1.0'

        repositorio_gndi_ci = "${env.WORKSPACE}\\cit-gndi-ci"
        repositorio_gndi_svn = "${env.WORKSPACE}\\cit-gndi-svn-branches\\${params.BRANCH_NAME}"
        remote_url = "https://192.168.20.242/svn/CADASTRO/SACI/branches/${params.BRANCH_NAME}"

        build_solution = "${env.repositorio_gndi_svn}\\${env.solution_name}"
        build_arguments = "/t:Publish /p:configuration=Release /p:assemblyname=\"${env.assembly_name_prd}\" /m:4 /v:quiet"
	}

    stages {
        stage('Checkout GIT - CI'){
            steps{
                dir(env.repositorio_gndi_ci){
                    checkout(   changelog: false, 
                                poll: false, 
                                scm: [ $class: 'GitSCM', 
                                        branches: [[name: '*/master']], 
                                        doGenerateSubmoduleConfigurations: false, 
                                        extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true]],
                                        userRemoteConfigs: [[credentialsId: 'ssh-int-jenkins', url: 'git@bitbucket.org:ciandt_it/cit-gndi-ci.git']]])
                }
            }
        }

        stage('Checkout SVN - Branch'){
            steps{
                dir(env.repositorio_gndi_svn){
                    script{
                        checkout(   [  $class: 'SubversionSCM', 
                                        locations: [[   credentialsId: 'svnGNDICredential', 
                                                        depthOption: 'infinity',
                                                        ignoreExternalsOption: true,
                                                        local: ".",
                                                        remote: "${env.remote_url}"]], 
                                        additionalCredentials: [], 
                                        excludedCommitMessages: '', 
                                        excludedRegions: '', 
                                        excludedRevprop: '', 
                                        excludedUsers: '', 
                                        filterChangelog: true, 
                                        ignoreDirPropChanges: false, 
                                        includedRegions: '', 
                                        workspaceUpdater: [$class: 'UpdateWithCleanUpdater']])
                    }
                }
            }
        }

        stage('Build & Sonar') {
            steps{
                bat """ 
                    \"${tool 'Scanner MsBuild 5'}\\SonarScanner.MSBuild.exe\" begin /k:\"SACI\" /v:${params.BRANCH_NAME}
                    \"${tool 'Msbuild 2017'}\\MSBuild.exe\" \"${env.build_solution}\" ${env.build_arguments}
                    \"${tool 'Scanner MsBuild 5'}\\SonarScanner.MSBuild.exe\" end
                    """
            }
        }
    }
    post {
        always {
            echo "Build nro. ${env.BUILD_NUMBER} finalizada!"
        }
        success {
            echo "A branch ${params.BRANCH_NAME} passou nos testes de qualidade definidas no SonarQube."
        }
        failure {
            echo "Falha no processo de build. Analise o console log do build ${env.BUILD_NUMBER}"
        }
    }
}