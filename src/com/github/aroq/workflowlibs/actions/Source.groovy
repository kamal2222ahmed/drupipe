package com.github.aroq.workflowlibs.actions

def add(params) {
    def source = params.source
    def result
    switch (source.type) {
        case 'git':
            dir(source.path) {
                git url: source.url, branch: source.branch
            }
            result = source.path
            break

        case 'dir':
            result = source.path
            break

        case 'docmanDocroot':
            result = executePipelineAction(action: 'Docman.init', params: [path: 'docroot']) {
                p = params
            }
            break
    }
    if (!params.sources) {
        params.sources = [:]
    }
    utils = new com.github.aroq.workflowlibs.Utils()
    if (result) {
        params.sources[source.name] = new com.github.aroq.workflowlibs.Source(name: source.name, type: source.type, path: source.path)

    }
    params.remove('source')
    params
}

def loadConfig(params) {
    configFilePath = sourcePath(params, params.sourceName, params.configPath)

    if (params.configType == 'groovy') {
        params << executePipelineAction(action: 'GroovyFileConfig.load', params: [configFileName: configFilePath]) {
            p = params
        }
    }
    params.remove('sourceName')
    params.remove('configPath')
    params.remove('configType')
    params
}
