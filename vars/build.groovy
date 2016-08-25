def call(body) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()

//    def config = configVault.config
//    config = configHelper.config
//    config << params
//    dump(config)

    stage 'build'
    config2 = configHelper.config

    for (action in params.actions) {
        try {
            def values = action.split("\\.")
            def actionInstance = this.class.classLoader.loadClass("com.github.aroq.workflowlibs.${values[0]}", true, false )?.newInstance()
            def methodName = values[1]
            actionInstance."$methodName"(params)
        }
        catch (err) {
            echo "Action ${action} is not exists."
            echo err.toString()
            throw err
        }
    }

}
