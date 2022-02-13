@file:JvmName("BoosterUtils")

package com.willfp.boosters

import com.willfp.boosters.boosters.ActivatedBooster
import com.willfp.boosters.boosters.Booster
import com.willfp.boosters.boosters.Boosters
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.profile
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.*

private val plugin = BoostersPlugin.instance

object BoosterUtils {
    private var active: ActivatedBooster? = null

    private val boosterKey = PersistentDataKey(
        plugin.namespacedKeyFactory.create("active_booster"),
        PersistentDataKeyType.STRING,
        ""
    )

    var shouldUseSQL = false

    fun getActiveBooster(): ActivatedBooster? {
        if (shouldUseSQL) {
            val key = Bukkit.getServer().profile.read(boosterKey)

            return if (key.isEmpty()) {
                null
            } else {
                val booster = key.split("::")
                val id = booster[0]
                val uuid = UUID.fromString(booster[1])
                ActivatedBooster(
                    Boosters.getByID(id) ?: return null,
                    uuid
                )
            }
        } else {
            return active
        }
    }

    fun setActiveBooster(booster: ActivatedBooster?) {
        if (shouldUseSQL) {
            if (booster == null) {
                Bukkit.getServer().profile.write(boosterKey, "")
            } else {
                Bukkit.getServer().profile.write(boosterKey, "${booster.booster.id}::${booster.player}")
            }
        } else {
            active = booster
        }
    }
}

val OfflinePlayer.boosters: List<Booster>
    get() {
        val found = mutableListOf<Booster>()

        for (booster in Boosters.values()) {
            val amount = this.profile.read(booster.dataKey)
            for (i in 0 until amount) {
                found.add(booster)
            }
        }

        return found
    }

fun OfflinePlayer.getAmountOfBooster(booster: Booster): Int {
    return this.profile.read(booster.dataKey)
}

fun OfflinePlayer.setAmountOfBooster(booster: Booster, amount: Int) {
    this.profile.write(booster.dataKey, amount)
}

fun OfflinePlayer.incrementBoosters(booster: Booster, amount: Int) {
    this.setAmountOfBooster(booster, this.getAmountOfBooster(booster) + amount)
}

fun Player.activateBooster(booster: Booster): Boolean {
    val amount = this.getAmountOfBooster(booster)

    if (amount <= 0) {
        return false
    }

    this.setAmountOfBooster(booster, amount - 1)

    for (activationMessage in booster.getActivationMessages(this)) {
        Bukkit.broadcastMessage(activationMessage)
    }

    plugin.scheduler.runLater(booster.duration.toLong()) {
        for (expiryMessage in booster.expiryMessages) {
            Bukkit.broadcastMessage(expiryMessage)
        }
        BoosterUtils.setActiveBooster(null)
    }

    BoosterUtils.setActiveBooster(ActivatedBooster(booster, this.uniqueId))

    for (player in Bukkit.getOnlinePlayers()) {
        player.playSound(
            player.location,
            Sound.UI_TOAST_CHALLENGE_COMPLETE,
            2f,
            0.9f
        )
    }

    return true
}