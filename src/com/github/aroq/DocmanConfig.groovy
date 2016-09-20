package com.github.aroq

import groovy.json.JsonSlurper

/**
 * Created by Aroq on 06/06/16.
 */
class DocmanConfig {

    def docrootConfigJson

    def docmanConfig

    def init() {
        docmanConfig = JsonSlurper.newInstance().parseText(docrootConfigJson)
    }

    def getProjects() {
        init()
        docmanConfig['projects']
    }

    def getStates() {
        init()
        docmanConfig['states']
    }

    def getEnvironments() {
        init()
        docmanConfig['environments']
    }

    def getVersionBranch(project, stateName) {
        init()
        if (docmanConfig.projects[project]['states'][stateName]['version']) {
            docmanConfig.projects[project]['states'][stateName]['version']
        }
        else if (docmanConfig.projects[project]['states'][stateName]['source']) {
            docmanConfig.projects[project]['states'][stateName]['source']['branch']
        }
    }

    def projectNameByGroupAndRepoName(groupName, repoName) {
        init()
        String result = ''
        docmanConfig.projects?.each { projectName, project ->
            result += " ${projectName} - ${project['repo']} : ${groupName} - ${repoName}"
            if (project['repo']?.contains("${groupName}/${repoName}")) {
                result = projectName
            }
        }
        result
    }

}
