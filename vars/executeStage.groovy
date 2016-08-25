def call(name, body) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()

    stage name

    for (action in params.actions) {
        try {
            def values = action.split("\\.")
            def actionInstance = this.class.classLoader.loadClass("com.github.aroq.workflowlibs.${values[0]}", true, false )?.newInstance()
            def methodName = values[1]
            dump(params, "Action params")
            actionInstance."$methodName"(params)
        }
        catch (err) {
            echo "Action ${action} is not exists."
            echo err.toString()
            throw err
        }
    }

}
