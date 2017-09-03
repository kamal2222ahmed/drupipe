package com.github.aroq.drupipe

class DrupipePipeline implements Serializable {

    ArrayList<DrupipeBlock> blocks = []

    LinkedHashMap context = [:]

    LinkedHashMap params = [:]

    LinkedHashMap config = [:]

    def script

    def utils

    def execute(body = null) {
        context.pipeline = this
        context.jenkinsParams = params
        utils = new com.github.aroq.drupipe.Utils()

        try {
            script.timestamps {
                script.node('master') {
                    utils.dump(params, 'PIPELINE-PARAMS')
                    utils.dump(config, 'PIPELINE-CONFIG')
                    context.utils = utils
                    params.debugEnabled = params.debugEnabled && params.debugEnabled != '0' ? true : false

                    def configParams = script.drupipeAction([action: 'Config.perform', params: [jenkinsParams: params]], context.clone() << params)
                    context << (configParams << config << context)
                    utils.dump(context, 'PIPELINE-CONTEXT')
                    // Secret option for emergency remove workspace.
                    if (context.force == '11') {
                        script.echo 'FORCE REMOVE DIR'
                        script.deleteDir()
                    }
                }

                if (!blocks) {
                    if (context.jobs) {
                        def job = getJobConfigByName(context.env.JOB_NAME)
                        if (job) {
                            utils.pipelineNotify(context, [name: 'Build', status: 'STARTED', level: 'build'])
                            context.job = job
                            utils.jsonDump(job, 'JOB')
                            def pipelineBlocks = job.pipeline && job.pipeline.blocks ? job.pipeline.blocks : []
                            if (pipelineBlocks) {
                                for (def i = 0; i < pipelineBlocks.size(); i++) {
                                    if (context.blocks && context.blocks[pipelineBlocks[i]]) {
                                        blocks << context.blocks[pipelineBlocks[i]]
                                    }
                                    else {
                                        script.echo "No pipeline block: ${pipelineBlocks[i]} is defined."
                                    }
                                }
                            }
                            else {
                                // TODO: to remove after updating all configs.
                                script.node('master') {
                                    def yamlFileName = job.pipeline.file ? job.pipeline.file : "pipelines/${context.env.JOB_BASE_NAME}.yaml"
                                    def pipelineYamlFile = "${context.projectConfigPath}/${yamlFileName}"
                                    if (script.fileExists(pipelineYamlFile)) {
                                        blocks = script.readYaml(file: pipelineYamlFile).blocks
                                    }
                                }
                            }
                        }
                        else {
                            script.echo "No job config is defined"
                        }
                    }
                    // TODO: to remove after updating all configs.
                    else {
                        script.echo "No jobs are defined in config"
                        script.node('master') {
                            def yamlFileName = "pipelines/${context.env.JOB_BASE_NAME}.yaml"
                            def pipelineYamlFile = "${context.projectConfigPath}/${yamlFileName}"
                            if (script.fileExists(pipelineYamlFile)) {
                                blocks = script.readYaml(file: pipelineYamlFile).blocks
                            }
                        }
                    }
                }

                if (blocks) {
                    if (context.containerMode == 'kubernetes') {
                        def nodeName = 'drupipe'
                        def containers = []
                        for (def i = 0; i < blocks.size(); i++) {
                            containers << script.containerTemplate(name: "block-${i}", image: blocks[i].dockerImage, ttyEnabled: true, command: 'cat', alwaysPullImage: true)
                        }
                        script.podTemplate(label: nodeName, containers: containers) {
                            script.node(nodeName) {
                                executeBlocks()
                            }
                        }

                    } else {
                        executeBlocks()
                    }
                }
                else {
                    script.echo "No pipeline blocks defined"
                }

                if (body) {
                    def result = body(context)
                    if (result) {
                        context << result
                    }
                }
            }
        }
        catch (e) {
            script.currentBuild.result = "FAILED"
            throw e
        }
        finally {
            utils.pipelineNotify(context, [name: 'Build', status: script.currentBuild.result, level: 'build'])

            context
        }
    }

    def executeBlocks() {
        for (def i = 0; i < blocks.size(); i++) {
            blocks[i].name = "blocks-${i}"
            def block = new DrupipeBlock(blocks[i])
//            script.echo 'BLOCK EXECUTE START'
            context << block.execute(context)
//            script.echo 'BLOCK EXECUTE END'
        }
    }

    def getJobConfigByName(String name) {
        def parts = name.split('/').drop(1)
        getJobConfig(context.jobs, parts, 0, [:])
    }

    def getJobConfig(jobs, parts, counter = 0, r = [:]) {
        script.echo "Counter: ${counter}"
        def part = parts[counter]
        script.echo "Part: ${part}"
        def j = jobs[part] ? jobs[part] : [:]
        if (j) {
            def children = j.containsKey('children') ? j['children'] : [:]
            j.remove('children')
            r = utils.merge(r, j)
            if (children) {
                getJobConfig(children, parts, counter + 1, r)
            }
            else {
                r
            }
        }
        else {
            [:]
        }
    }

    def executeStages(stagesToExecute, context) {
        def stages = processStages(stagesToExecute, context)
        stages += processStages(context.stages, context)

        for (int i = 0; i < stages.size(); i++) {
            context << stages[i].execute(context)
        }
        context
    }

    @NonCPS
    List<DrupipeStage> processStages(stages, context) {
        List<DrupipeStage> result = []
        for (item in stages) {
            result << processStage(item, context)
        }
        result
    }

    @NonCPS
    DrupipeStage processStage(s, context) {
        if (!(s instanceof DrupipeStage)) {
            //new DrupipeStage(name: stage.key, params: context, actions: processPipelineActionList(stage.value, context))
            s = new DrupipeStage(s)
        }
        if (s instanceof DrupipeStage) {
            for (action in s.actions) {
                def values = action.action.split("\\.")
                if (values.size() > 1) {
                    action.name = values[0]
                    action.methodName = values[1]
                }
                else {
                    action.name = 'PipelineController'
                    action.methodName = values[0]
                }
            }
            s
        }
    }

    @NonCPS
    List processPipelineActionList(actionList, context) {
        List actions = []
        for (action in actionList) {
            actions << processPipelineAction(action, context)
        }
        actions
    }

    @NonCPS
    DrupipeAction processPipelineAction(action, context) {
        def actionName
        def actionMethodName
        def actionParams
        if (action.getClass() == java.lang.String) {
            actionName = action
            actionParams = [:]
        }
        else {
            actionName = action.action
            actionParams = action.params
        }
        def values = actionName.split("\\.")
        if (values.size() > 1) {
            actionName = values[0]
            actionMethodName = values[1]
        }
        else {
            actionName = 'PipelineController'
            actionMethodName = values[0]
        }

        new DrupipeAction(name: actionName, methodName: actionMethodName, params: actionParams, context: context)
    }

    def executePipelineActionList(actions, context) {
        def actionList = processPipelineActionList(actions, context)
        def result = [:]
        try {
            for (action in actionList) {
                def actionResult = action.execute(result)
                result = utils.merge(result, actionResult)
            }
            result
        }
        catch (err) {
            script.echo err.toString()
            throw err
        }
    }

}
