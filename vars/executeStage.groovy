def call(name, body) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()

    if (params.p) {
        params << params.p
        params.remove('p')
    }

    stage params.stage.name

    try {
        for (action in params.stage.actionList) {
            def values = action.split("\\.")
            def actionInstance = this.class.classLoader.loadClass("com.github.aroq.workflowlibs.${values[0]}", true, false )?.newInstance()
            def methodName = values[1]
            dump(params, "${action} action params")
            actionResult = actionInstance."$methodName"(params)
            if (actionResult) {
                params << actionResult
            }
            dump(params, "${action} action result")
        }
        params.remove('stage')
        params
    }
    catch (err) {
        echo err.toString()
        throw err
    }
}
