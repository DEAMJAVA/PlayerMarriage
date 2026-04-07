package net.deamjava.player_marriage.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands
import net.minecraft.commands.CommandSourceStack
object McCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("mc")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val msg = StringArgumentType.getString(ctx, "message")
                        MarryCommand.sendPartnerChat(ctx.source, msg)
                    })
        )
    }
}
