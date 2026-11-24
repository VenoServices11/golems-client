// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.controller

import dev.golemsclient.oc.client.bedrock.molang.MolangExpr
import dev.golemsclient.oc.client.bedrock.molang.MolangStatement

data class BedrockAnimationControllerFile(
    val controllers: Map<String, BedrockAnimationController>,
)

data class BedrockAnimationController(
    val name: String,
    val initialState: String,
    val states: Map<String, ControllerState>,
)

data class ControllerState(
    val name: String,
    val animations: List<StateAnimation>,
    val onEntry: List<MolangStatement> = emptyList(),
    val onExit: List<MolangStatement> = emptyList(),
    val transitions: List<ControllerTransition> = emptyList(),
)

data class StateAnimation(
    val animationName: String,
    val weight: MolangExpr = MolangExpr.Number(1.0),
)

data class ControllerTransition(
    val targetState: String,
    val condition: MolangExpr,
)
