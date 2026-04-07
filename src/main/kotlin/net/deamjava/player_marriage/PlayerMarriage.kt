package net.deamjava.player_marriage

import net.deamjava.player_marriage.commands.MarryCommand
import net.deamjava.player_marriage.commands.McCommand
import net.deamjava.player_marriage.config.ModConfig
import net.deamjava.player_marriage.data.MarriageData
import net.deamjava.player_marriage.events.ChatHandler
import net.deamjava.player_marriage.events.KissInteractHandler
import net.deamjava.player_marriage.events.PvpHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory

object PlayerMarriage : ModInitializer {

    const val MOD_ID = "player-marriage"
    val logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("PlayerMarriage initializing...")
        ModConfig.init()

        // Load data when server starts
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            MarriageData.init(server)
        }

        // Save data when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            MarriageData.save()
        }

        // Track last seen on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            MarriageData.updateLastSeen(handler.player.stringUUID)
            MarriageData.save()
        }

        // Register commands
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            MarryCommand.register(dispatcher)
            McCommand.register(dispatcher)
        }

        // Register events
        KissInteractHandler.register()
        PvpHandler.register()
        ChatHandler.register()

        logger.info("PlayerMarriage initialized!")
    }
}
