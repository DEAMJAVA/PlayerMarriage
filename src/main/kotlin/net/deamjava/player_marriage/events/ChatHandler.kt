package net.deamjava.player_marriage.events

import net.deamjava.player_marriage.data.MarriageData
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object ChatHandler {

    fun register() {
        // Allow-all check: if player has chat toggled on, we want to redirect.
        // We use ServerMessageEvents.ALLOW_CHAT_MESSAGE to cancel the public message,
        // then manually deliver it to the partner only.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, _ ->
            val player = sender as? ServerPlayer ?: return@register true
            if (!MarriageData.chatToggled.contains(player.stringUUID)) return@register true

            if (!MarriageData.isMarried(player.stringUUID)) {
                MarriageData.chatToggled.remove(player.stringUUID)
                return@register true
            }

            val partnerUuid = MarriageData.getPartnerUuid(player.stringUUID)!!
            val partner = MarriageData.getOnlinePlayer(partnerUuid)

            val chatMsg = Component.literal("")
                .append(Component.literal("❤ [Private] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(player.name.string).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(message.decoratedContent().string).withStyle(ChatFormatting.WHITE))

            player.sendSystemMessage(chatMsg)
            partner?.sendSystemMessage(chatMsg) ?: player.sendSystemMessage(
                Component.literal("(Partner is offline)").withStyle(ChatFormatting.GRAY)
            )

            false // cancel public broadcast
        }
    }
}
