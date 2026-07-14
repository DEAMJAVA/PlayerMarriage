package net.deamjava.player_marriage.events

import net.deamjava.player_marriage.commands.MarryCommand
import net.deamjava.player_marriage.config.ModConfig
import net.deamjava.player_marriage.data.MarriageData
import net.deamjava.player_marriage.util.Msg
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import java.time.Instant

object KissInteractHandler {

    // uuid -> last kiss timestamp (for cooldown)
    private val kissCooldowns = mutableMapOf<String, Long>()
    fun register() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            val cfg = ModConfig.values
            if (world.isClientSide) return@register InteractionResult.PASS
            if (!cfg.enableShiftRightClickKiss) return@register InteractionResult.PASS
            if (!player.isShiftKeyDown) return@register InteractionResult.PASS
            if (entity !is Player) return@register InteractionResult.PASS

            val serverPlayer = player as? ServerPlayer ?: return@register InteractionResult.PASS
            val targetPlayer = entity as? ServerPlayer ?: return@register InteractionResult.PASS

            if (!MarriageData.isMarried(serverPlayer.stringUUID)) return@register InteractionResult.PASS

            val partnerUuid = MarriageData.getPartnerUuid(serverPlayer.stringUUID)
            if (partnerUuid != targetPlayer.stringUUID) return@register InteractionResult.PASS

            // Cooldown check
            val now = Instant.now().epochSecond
            val cooldownSeconds = cfg.kissCooldownSeconds.coerceAtLeast(0)
            val lastKiss = kissCooldowns[serverPlayer.stringUUID] ?: 0L
            if (now - lastKiss < cooldownSeconds) {
                val remaining = cooldownSeconds - (now - lastKiss)
                serverPlayer.sendOverlayMessage(Msg.info("Wait ${remaining}s before kissing again!"))
                return@register InteractionResult.SUCCESS
            }

            kissCooldowns[serverPlayer.stringUUID] = now
            MarryCommand.spawnKissEffects(serverPlayer, targetPlayer)
            InteractionResult.SUCCESS
        }
    }
}
