package net.deamjava.player_marriage.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object Msg {
    fun error(msg: String): MutableComponent =
        Component.literal("✗ ").withStyle(ChatFormatting.DARK_RED)
            .append(Component.literal(msg).withStyle(ChatFormatting.RED))

    fun info(msg: String): MutableComponent =
        Component.literal("ℹ ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(msg).withStyle(ChatFormatting.GRAY))

    fun heart(msg: String): MutableComponent =
        Component.literal("❤ ").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(msg).withStyle(ChatFormatting.WHITE))
}
