def call(context = [:], body) {
    context << context.defaultActionParams['drupipeWithKubernetes'] << context
    container(context.dockerImage) {
        context.workspace = pwd()
        drupipeShell("echo 'test'", context)
        //sshagent([context.credentialsId]) {
//            drupipeShell("git config --global user.email 'drupipe@github.com'; git config --global user.name 'Drupipe'", context)
            result = body(context)
            if (result) {
                context << result
            }
        //}
    }

    context
}
