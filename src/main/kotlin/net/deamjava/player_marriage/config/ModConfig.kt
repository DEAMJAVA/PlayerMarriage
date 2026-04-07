package net.deamjava.player_marriage.config

import com.google.gson.GsonBuilder
import net.deamjava.player_marriage.PlayerMarriage
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class PlayerMarriageConfig(
    val kissCooldownSeconds: Long = 5,
    val proposalExpirySeconds: Long = 300,
    val divorceConfirmSeconds: Long = 30,
    val enableShiftRightClickKiss: Boolean = true,
    val enableAdultCommands: Boolean = false,
    val sexCommandCooldownSeconds: Long = 120
)

object ModConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: File = FabricLoader.getInstance().configDir.resolve("player-marriage/config.json").toFile()

    @Volatile
    var values: PlayerMarriageConfig = PlayerMarriageConfig()
        private set

    fun init() {
        configFile.parentFile?.mkdirs()
        if (!configFile.exists()) {
            save()
            PlayerMarriage.logger.info("Created default config at {}", configFile.absolutePath)
            return
        }

        try {
            val parsed = gson.fromJson(configFile.readText(), PlayerMarriageConfig::class.java)
            values = parsed ?: PlayerMarriageConfig()
        } catch (e: Exception) {
            PlayerMarriage.logger.error("Failed to load config, using defaults", e)
            values = PlayerMarriageConfig()
            save()
        }
    }

    fun save() {
        configFile.parentFile?.mkdirs()
        configFile.writeText(gson.toJson(values))
    }
}
