package com.github.aroq.drupipe.actions

import com.github.aroq.drupipe.DrupipeAction
import groovy.json.JsonSlurperClassic

class Jenkins extends BaseAction {

    def context

    def script

    def utils

    def DrupipeAction action

    def cli() {
        def inventory = script.readJSON file: 'terraform.inventory.json'
        def creds = [this.script.string(credentialsId: 'JENKINS_API_TOKEN', variable: 'JENKINS_API_TOKEN')]
        this.script.withCredentials(creds) {
            def envvars = ["JENKINS_URL=http://${inventory['zebra-jenkins-master'][0]}:${this.action.params.port}"]
            this.script.withEnv(envvars) {
                this.script.drupipeShell("""
                /jenkins-cli/jenkins-cli-wrapper.sh -auth ${this.action.params.user}:\${JENKINS_API_TOKEN} ${this.action.params.command}
                """, this.context)
            }
        }
    }

    def build() {
        this.action.params.command = "build -s -v ${this.action.params.jobName}"
        cli()
    }

    def seedTest() {
        def projects = jsonParseProjects(this.script.readFile("mothership/projects.json")).tokenize(',')
        for (def i = 0; i < projects.size(); i++) {
            this.script.echo projects[i]
            this.action.params.jobName = "${projects[i]}/seed"
            try {
                build()
            }
            catch (e) {
                // TODO: Detect the reason of fail.
                script.echo "Build was UNSTABLE or FAILED"
            }
        }
    }

    @NonCPS
    def jsonParseProjects(def json) {
        def result = []
        def projects = (new groovy.json.JsonSlurperClassic().parseText(json)).projects
        for (project in projects) {
            if (project.value.containsKey('tests') && project.value['tests'].contains('seed')) {
                result << project.key
            }
        }
        result.join(',')
    }

}