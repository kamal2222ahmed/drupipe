def call(name, body) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()

    stage name

    def result = params

    for (action in params.actions) {
        try {
            def values = action.split("\\.")
            def actionInstance = this.class.classLoader.loadClass("com.github.aroq.workflowlibs.${values[0]}", true, false )?.newInstance()
            def methodName = values[1]
            dump(params, "Action params")
            actionResult = actionInstance."$methodName"(params)
            if (actionResult) {
               result << actionResult
            }
            dump(result, "Action result")
            result
        }
        catch (err) {
            echo "Action ${action} is not exists."
            echo err.toString()
            throw err
        }
    }

}
