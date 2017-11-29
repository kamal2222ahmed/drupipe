import com.github.aroq.drupipe.DrupipeContainerBlock
import com.github.aroq.drupipe.DrupipeController

def call(ArrayList containers, DrupipeController controller, ArrayList unstash = [], ArrayList stash = [], unipipe_retrieve_config = false) {
    echo "Container mode: kubernetes"
    def nodeName = 'drupipe'
    def containerNames = []
    def containersToExecute= []

    for (def i = 0; i < containers.size(); i++) {
        def container = containers[i]
        controller.utils.debugLog(controller.context, container, 'CONTAINER TEMPLATE', [debugMode: 'json'], [], true)
        def containerName = container.name.replaceAll('\\.','-').replaceAll('_','-')
        if (!containerNames.contains(containerName)) {
            echo "Create k8s containerTemplate for container: ${container.name}, image: ${container.image}"
            containerNames += containerName
            containersToExecute.add(containerTemplate(
                name:                  containerName,
                image:                 container.image,
                ttyEnabled:            container.k8s.ttyEnabled,
                command:               container.k8s.command,
                resourceRequestCpu:    container.k8s.resourceRequestCpu,
                resourceLimitCpu:      container.k8s.resourceLimitCpu,
                resourceRequestMemory: container.k8s.resourceRequestMemory,
                resourceLimitMemory:   container.k8s.resourceLimitMemory,
                alwaysPullImage:       container.k8s.alwaysPullImage,
                // TODO: move in configs.
                envVars: [
                    envVar(key: 'TF_VAR_consul_address', value: controller.context.env.TF_VAR_consul_address),
                    envVar(key: 'UNIPIPE_SOURCES', value: controller.context.env.UNIPIPE_SOURCES),
                    secretEnvVar(key: 'DIGITALOCEAN_TOKEN', secretName: 'zebra-keys', secretKey: 'zebra_do_token'),
//                  secretEnvVar(key: 'CONSUL_ACCESS_TOKEN', secretName: 'zebra-keys', secretKey: 'zebra_consul_access_token'),
                    secretEnvVar(key: 'ANSIBLE_VAULT_PASS_FILE', secretName: 'zebra-keys', secretKey: 'zebra_ansible_vault_pass'),
                    secretEnvVar(key: 'GITLAB_API_TOKEN_TEXT', secretName: 'zebra-keys', secretKey: 'zebra_gitlab_api_token'),
                    secretEnvVar(key: 'GCLOUD_ACCESS_KEY', secretName: 'zebra-keys', secretKey: 'zebra_gcloud_key'),
                    secretEnvVar(key: 'HELM_ZEBRA_SECRETS_FILE', secretName: 'zebra-keys', secretKey: 'zebra_cicd_helm_secret_values'),
                ],
            ))
        }
    }

//    def creds = [string(credentialsId: 'DO_TOKEN', variable: 'DIGITALOCEAN_TOKEN')]
//    withCredentials(creds) {
    podTemplate(
        label: nodeName,
        containers: containersToExecute,
    ) {
        node(nodeName) {
            if (unipipe_retrieve_config) {
                controller.utils.log "Retrieve config."
                controller.utils.getUnipipeConfig(controller)
            }
            else {
                controller.utils.log "Retrieve config disabled in config."
            }
            controller.utils.unstashList(controller, unstash)
            controller.context.workspace = pwd()
            for (def i = 0; i < containers.size(); i++) {
                container(containers[i].name.replaceAll('\\.','-').replaceAll('_','-')) {
                    for (block in containers[i].blocks) {
//                            controller.utils.debugLog(controller.context, block, 'CONTAINER BLOCK', [debugMode: 'json'], [], true)
                        sshagent([controller.context.credentialsId]) {
                            def drupipeContainerBlock = new DrupipeContainerBlock(block)
                            drupipeContainerBlock.controller = controller
                            drupipeContainerBlock.execute()
                        }
                    }
                }
            }
            controller.utils.stashList(controller, stash)
        }
    }
//    }

}
