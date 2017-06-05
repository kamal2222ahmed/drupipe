package com.github.aroq.drupipe.actions

import com.github.aroq.drupipe.DrupipeAction
import groovy.json.JsonOutput

class AcquiaHandler extends BaseAction {

    def context

    def script

    def utils

    def DrupipeAction action


    def deploy() {
        script.drupipeAction([action: 'Druflow.deploy', params: this.action.params], context)
    }

}