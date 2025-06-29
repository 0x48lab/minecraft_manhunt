package com.hacklab.manhunt

import org.bukkit.entity.Player

enum class PlayerRole {
    RUNNER,
    HUNTER,
    SPECTATOR
}

data class ManhuntPlayer(
    val player: Player,
    var role: PlayerRole
)