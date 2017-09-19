package com.github.aroq.drupipe.actions

import com.github.aroq.drupipe.DrupipeAction
import groovy.json.JsonSlurperClassic

class Jenkins extends BaseAction {

    def context

    def script

    def utils

    def DrupipeAction action

    def getJenkinsAddress() {
        String terraformEnv = this.context.jenkinsParams.terraformEnv
        def creds = [script.string(credentialsId: 'CONSUL_ACCESS_TOKEN', variable: 'CONSUL_ACCESS_TOKEN')]
        def result = script.withCredentials(creds) {
            this.script.drupipeShell("""
                curl http://\${TF_VAR_consul_address}/v1/kv/zebra/jenkins/${terraformEnv}/address?raw&token=\${CONSUL_ACCESS_TOKEN}
            """, this.context.clone() << [drupipeShellReturnStdout: true])
        }
        result.drupipeShellResult
    }

    def getJenkinsSlaveAddress() {
        String terraformEnv = this.context.jenkinsParams.terraformEnv
        def creds = [script.string(credentialsId: 'CONSUL_ACCESS_TOKEN', variable: 'CONSUL_ACCESS_TOKEN')]
        def result = script.withCredentials(creds) {
            this.script.drupipeShell("""
                curl http://\${TF_VAR_consul_address}/v1/kv/zebra/jenkins/${terraformEnv}/slave/address?raw&token=\${CONSUL_ACCESS_TOKEN}
            """, this.context.clone() << [drupipeShellReturnStdout: true])
        }
        result.drupipeShellResult
    }


    def executeAnsiblePlaybook() {
        // TODO: refactor terraformEnv params into common env param.
        action.params.playbookParams << [env: context.jenkinsParams.terraformEnv, jenkins_default_slave_address: getJenkinsSlaveAddress()]
        action.params.inventoryArgument = getJenkinsAddress() + ','
        script.drupipeAction([action: 'Ansible.executeAnsiblePlaybook', params: [action.params]], context)
    }

    def cli() {
        def creds = [this.script.string(credentialsId: 'JENKINS_API_TOKEN', variable: 'JENKINS_API_TOKEN')]
        this.script.withCredentials(creds) {
            def envvars = ["JENKINS_URL=http://${getJenkinsAddress()}:${this.action.params.port}"]
            this.script.withEnv(envvars) {
                this.script.drupipeShell("""
                /jenkins-cli/jenkins-cli-wrapper.sh -auth ${this.action.params.user}:\${JENKINS_API_TOKEN} ${this.action.params.command}
                """, this.context)
            }
        }
    }

    def build() {
        this.action.params.command = "build -s ${this.action.params.jobName}"
        cli()
    }

    def seedTest() {
        def mothershipConfig = utils.getMothershipConfigFile(context)
        def projects = parseProjects(mothershipConfig).tokenize(',')
        for (def i = 0; i < projects.size(); i++) {
            this.script.echo projects[i]
            this.action.params.jobName = "${projects[i]}/seed"
            build()
        }
    }

    @NonCPS
    def parseProjects(def projects) {
        def result = []
        for (project in projects) {
            if (project.value.containsKey('tests') && project.value['tests'].contains('seed')) {
                result << project.key
            }
        }
        result.join(',')
    }

}
