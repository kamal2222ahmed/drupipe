package com.github.aroq.drupipe

class DrupipeBlock implements Serializable {

    ArrayList<DrupipeStage> stages = []

    String nodeName = 'use_default'

    Boolean withDocker = false

    String dockerImage = 'use_default'

    LinkedHashMap context = [:]

    LinkedHashMap config = [:]

    String name

    def execute(c, body = null) {
        def utils = new com.github.aroq.drupipe.Utils()
        if (c) {
            this.context = c
        }

        this.context = utils.merge(this.context, this.config)

        if (nodeName == 'use_default') {
            nodeName = context.nodeName
        }

        if (withDocker && dockerImage == 'use_default') {
            dockerImage = context.dockerImage
        }
        context.dockerImage = dockerImage

        def result = [:]
        context.block = this

        if (nodeName) {
            utils.pipelineNotify(context, [name: "Block on ${nodeName}", status: 'START', level: 'block'])
            if (withDocker) {
                if (context.containerMode == 'kubernetes') {
                    context.pipeline.script.drupipeWithKubernetes(context) {
                        result = _execute(body)
                    }
                }
                else if (context.containerMode == 'docker') {
                    context.pipeline.script.echo "NODE NAME: ${nodeName}"
                    context.pipeline.script.node(nodeName) {
                        context.pipeline.script.unstash('config')
                        context.pipeline.script.drupipeWithDocker(context) {
                            result = _execute(body)
                        }
                    }
                }
            }
            else {
                context.pipeline.script.echo "NODE NAME: ${nodeName}"
                context.pipeline.script.node(nodeName) {
                    context.pipeline.script.unstash('config')
                    context.pipeline.script.sshagent([context.credentialsId]) {
                        result = _execute(body)
                    }
                }
            }
            utils.pipelineNotify(context, [name: "Block on ${nodeName}", status: 'END', level: 'block'])
        }
        else {
            result = _execute(body)
        }

        result
    }

    def _execute(body = null) {
        if (stages) {
            context << context.pipeline.executeStages(stages, context)
        }
        else {
            if (body) {
                def result = body()
                if (result) {
                    context << body()
                }
            }
        }
        context
    }
}
