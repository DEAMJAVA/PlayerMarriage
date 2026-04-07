package net.deamjava.player_marriage.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.deamjava.player_marriage.config.ModConfig
import net.deamjava.player_marriage.data.MarriageData
import net.deamjava.player_marriage.data.PendingProposal
import net.deamjava.player_marriage.util.Msg
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Relative
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MarryCommand {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val sexCooldowns = mutableMapOf<String, Long>()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("marry")
                .executes { ctx -> showHelp(ctx.source) }
                .then(Commands.literal("help")
                    .executes { ctx -> showHelp(ctx.source) })

                .then(Commands.argument("player", EntityArgument.player())
                    .executes { ctx ->
                        val target = EntityArgument.getPlayer(ctx, "player")
                        propose(ctx.source, target)
                    })

                .then(Commands.literal("accept")
                    .executes { ctx -> acceptProposal(ctx.source) })

                .then(Commands.literal("deny")
                    .executes { ctx -> denyProposal(ctx.source) })

                .then(Commands.literal("divorce")
                    .executes { ctx -> divorce(ctx.source) })

                .then(Commands.literal("tp")
                    .executes { ctx -> teleportToPartner(ctx.source) })

                .then(Commands.literal("sethome")
                    .executes { ctx -> setHome(ctx.source) })

                .then(Commands.literal("home")
                    .executes { ctx -> goHome(ctx.source) })

                .then(Commands.literal("delhome")
                    .executes { ctx -> delHome(ctx.source) })

                .then(Commands.literal("gift")
                    .executes { ctx -> gift(ctx.source) })

                .then(Commands.literal("chat")
                    .executes { ctx -> toggleChat(ctx.source) }
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val msg = StringArgumentType.getString(ctx, "message")
                            sendPartnerChat(ctx.source, msg)
                        }))

                .then(Commands.literal("kiss")
                    .executes { ctx -> kissCommand(ctx.source) })

                .then(Commands.literal("hug")
                    .executes { ctx -> hug(ctx.source) })

                .then(Commands.literal("sex")
                    .executes { ctx -> sex(ctx.source) })

                .then(Commands.literal("pvpon")
                    .executes { ctx -> setPvp(ctx.source, false) })

                .then(Commands.literal("pvpoff")
                    .executes { ctx -> setPvp(ctx.source, true) })

                .then(Commands.literal("seen")
                    .executes { ctx -> seen(ctx.source) })

                .then(Commands.literal("list")
                    .executes { ctx -> list(ctx.source) })
        )
    }

    // ─── /marry help ─────────────────────────────────────────────────────────

    private fun showHelp(source: CommandSourceStack): Int {
        source.sendSuccess({
            Component.literal("")
                .append(Component.literal("━━━ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("❤ PlayerMarriage ❤").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(" ━━━\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(helpLine("/marry <player>", "Propose to a player"))
                .append(helpLine("/marry accept / deny", "Accept or deny a proposal"))
                .append(helpLine("/marry divorce", "Divorce your partner"))
                .append(helpLine("/marry tp", "Teleport to partner"))
                .append(helpLine("/marry sethome", "Set your couple's home"))
                .append(helpLine("/marry home", "Teleport to couple's home"))
                .append(helpLine("/marry delhome", "Delete couple's home"))
                .append(helpLine("/marry gift", "Give held item to partner"))
                .append(helpLine("/marry chat [msg]", "Toggle/send private chat"))
                .append(helpLine("/mc <msg>", "Quick private chat message"))
                .append(helpLine("/marry kiss", "Kiss your partner ❤"))
                .append(helpLine("/marry hug", "Hug your partner"))
                .append(helpLine("/marry sex", "Adult roleplay command (if enabled)"))
                .append(helpLine("/marry pvpon/off", "Toggle PvP protection"))
                .append(helpLine("/marry seen", "When was partner last online"))
                .append(helpLine("/marry list", "List all couples"))
                .append(Component.literal("  Shift + Right-click").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" your partner to kiss them!\n").withStyle(ChatFormatting.GRAY))
        }, false)
        return 1
    }

    private fun helpLine(cmd: String, desc: String): Component =
        Component.literal("  ")
            .append(Component.literal(cmd).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("$desc\n").withStyle(ChatFormatting.GRAY))

    // ─── /marry <player> ─────────────────────────────────────────────────────

    private fun propose(source: CommandSourceStack, target: ServerPlayer): Int {
        val proposer = source.player ?: return 0

        if (proposer.uuid == target.uuid) {
            proposer.sendSystemMessage(Msg.error("You can't marry yourself!"))
            return 0
        }
        if (MarriageData.isMarried(proposer.getStringUUID())) {
            proposer.sendSystemMessage(Msg.error("You are already married! Divorce first."))
            return 0
        }
        if (MarriageData.isMarried(target.getStringUUID())) {
            proposer.sendSystemMessage(Msg.error("${target.name.string} is already married!"))
            return 0
        }
        if (MarriageData.getProposal(target.getStringUUID()) != null) {
            proposer.sendSystemMessage(Msg.error("${target.name.string} already has a pending proposal."))
            return 0
        }

        val proposal = PendingProposal(
            proposerUuid = proposer.getStringUUID(),
            proposerName = proposer.name.string,
            targetUuid = target.getStringUUID()
        )
        MarriageData.addProposal(proposal)

        proposer.sendSystemMessage(
            Msg.heart("You proposed to ")
                .append(Component.literal(target.name.string).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Msg.heart("! Waiting for their response..."))
        )

        val acceptBtn = Component.literal("[Accept]")
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            .withStyle { it.withClickEvent(ClickEvent.RunCommand("/marry accept")) }
        val denyBtn = Component.literal("[Deny]")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            .withStyle { it.withClickEvent(ClickEvent.RunCommand("/marry deny")) }

        target.sendSystemMessage(
            Component.literal("")
                .append(Component.literal("❤ ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(proposer.name.string).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" has proposed to you! ").withStyle(ChatFormatting.WHITE))
                .append(acceptBtn)
                .append(Component.literal(" "))
                .append(denyBtn)
        )
        return 1
    }

    // ─── /marry accept ───────────────────────────────────────────────────────

    private fun acceptProposal(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        val proposal = MarriageData.getProposal(player.getStringUUID())
        if (proposal == null) {
            player.sendSystemMessage(Msg.error("You have no pending proposal."))
            return 0
        }

        if (Instant.now().epochSecond - proposal.timestamp > ModConfig.values.proposalExpirySeconds) {
            MarriageData.removeProposal(player.getStringUUID())
            player.sendSystemMessage(Msg.error("That proposal has expired."))
            return 0
        }

        val proposer = MarriageData.getOnlinePlayer(proposal.proposerUuid)
        if (proposer == null) {
            MarriageData.removeProposal(player.getStringUUID())
            player.sendSystemMessage(Msg.error("${proposal.proposerName} is no longer online."))
            return 0
        }

        MarriageData.removeProposal(player.getStringUUID())
        MarriageData.marry(proposer, player)

        val announcement = Component.literal("")
            .append(Component.literal("❤ ").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(proposer.name.string).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" and ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(player.name.string).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" are now married! Congratulations! ❤").withStyle(ChatFormatting.LIGHT_PURPLE))

        source.server.playerList.players.forEach { it.sendSystemMessage(announcement) }
        return 1
    }

    // ─── /marry deny ─────────────────────────────────────────────────────────

    private fun denyProposal(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        val proposal = MarriageData.getProposal(player.getStringUUID())
        if (proposal == null) {
            player.sendSystemMessage(Msg.error("You have no pending proposal."))
            return 0
        }
        MarriageData.removeProposal(player.getStringUUID())
        player.sendSystemMessage(Msg.info("You denied the proposal."))

        val proposer = MarriageData.getOnlinePlayer(proposal.proposerUuid)
        proposer?.sendSystemMessage(Msg.error("${player.name.string} denied your proposal. 💔"))
        return 1
    }

    // ─── /marry divorce ──────────────────────────────────────────────────────

    private val pendingDivorce = mutableSetOf<String>()

    private fun divorce(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        if (!pendingDivorce.contains(player.getStringUUID())) {
            pendingDivorce.add(player.getStringUUID())
            player.sendSystemMessage(
                Component.literal("⚠ Are you sure you want to divorce? ").withStyle(ChatFormatting.YELLOW)
                    .append(
                        Component.literal("[Confirm]").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .withStyle { it.withClickEvent(ClickEvent.RunCommand("/marry divorce")) }
                    )
            )
            Thread {
                Thread.sleep(ModConfig.values.divorceConfirmSeconds.coerceAtLeast(1) * 1000)
                pendingDivorce.remove(player.getStringUUID())
            }.also { it.isDaemon = true }.start()
            return 1
        }

        pendingDivorce.remove(player.getStringUUID())
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        MarriageData.divorce(player.getStringUUID(), partnerUuid)

        val announcement = Component.literal("")
            .append(Component.literal("💔 ").withStyle(ChatFormatting.RED))
            .append(Component.literal(player.name.string).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" and ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(partnerName).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" have divorced. 💔").withStyle(ChatFormatting.RED))

        source.server.playerList.players.forEach { it.sendSystemMessage(announcement) }

        val partner = MarriageData.getOnlinePlayer(partnerUuid)
        partner?.sendSystemMessage(Msg.error("${player.name.string} has divorced you."))
        return 1
    }

    // ─── /marry tp ───────────────────────────────────────────────────────────

    private fun teleportToPartner(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        val partner = MarriageData.getOnlinePlayer(partnerUuid)
        if (partner == null) {
            player.sendSystemMessage(Msg.error("$partnerName is not online."))
            return 0
        }
        player.teleportTo(
            partner.level() as ServerLevel,
            partner.x,
            partner.y,
            partner.z,
            mutableSetOf<Relative>(),
            partner.yRot,
            partner.xRot,
            true
        )
        player.sendSystemMessage(Msg.heart("Teleported to $partnerName!"))
        partner.sendSystemMessage(Msg.heart("${player.name.string} teleported to you."))
        return 1
    }

    // ─── /marry sethome ──────────────────────────────────────────────────────

    private fun setHome(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val dim = player.level().dimension().toString()
        MarriageData.setHome(player.getStringUUID(), player.x, player.y, player.z, dim)
        player.sendSystemMessage(Msg.heart("Couple's home set!"))

        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        MarriageData.getOnlinePlayer(partnerUuid)
            ?.sendSystemMessage(Msg.heart("${player.name.string} set your couple's home."))
        return 1
    }

    // ─── /marry home ─────────────────────────────────────────────────────────

    private fun goHome(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        if (!MarriageData.hasHome(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("No couple's home set! Use /marry sethome first."))
            return 0
        }
        val record = MarriageData.getRecord(player.getStringUUID())!!
        val level: ServerLevel? = source.server.overworld()
        if (level == null) {
            player.sendSystemMessage(Msg.error("Home dimension is unavailable."))
            return 0
        }
        player.teleportTo(
            level,
            record.homeX!!,
            record.homeY!!,
            record.homeZ!!,
            mutableSetOf<Relative>(),
            player.yRot,
            player.xRot,
            true
        )
        player.sendSystemMessage(Msg.heart("Teleported to couple's home!"))
        return 1
    }

    // ─── /marry delhome ──────────────────────────────────────────────────────

    private fun delHome(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        if (!MarriageData.hasHome(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("No home is set."))
            return 0
        }
        MarriageData.deleteHome(player.getStringUUID())
        player.sendSystemMessage(Msg.heart("Couple's home deleted."))

        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        MarriageData.getOnlinePlayer(partnerUuid)
            ?.sendSystemMessage(Msg.info("${player.name.string} deleted your couple's home."))
        return 1
    }

    // ─── /marry gift ─────────────────────────────────────────────────────────

    private fun gift(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        val partner = MarriageData.getOnlinePlayer(partnerUuid)
        if (partner == null) {
            player.sendSystemMessage(Msg.error("$partnerName is not online."))
            return 0
        }
        val held = player.mainHandItem
        if (held.isEmpty) {
            player.sendSystemMessage(Msg.error("You're not holding anything!"))
            return 0
        }
        val itemName = held.hoverName.string
        val gift = held.copy()
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY)

        if (!partner.inventory.add(gift)) {
            partner.drop(gift, false)
        }

        player.sendSystemMessage(Msg.heart("You gifted $itemName to $partnerName! ❤"))
        partner.sendSystemMessage(
            Component.literal("❤ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(player.name.string).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" gifted you ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(itemName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("!").withStyle(ChatFormatting.WHITE))
        )
        return 1
    }

    // ─── /marry chat ─────────────────────────────────────────────────────────

    private fun toggleChat(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val on = MarriageData.chatToggled.contains(player.getStringUUID())
        if (on) {
            MarriageData.chatToggled.remove(player.getStringUUID())
            player.sendSystemMessage(Msg.info("Private chat disabled. Talking to everyone now."))
        } else {
            MarriageData.chatToggled.add(player.getStringUUID())
            player.sendSystemMessage(Msg.heart("Private chat enabled! Only your partner will see your messages."))
        }
        return 1
    }

    fun sendPartnerChat(source: CommandSourceStack, message: String): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        val partner = MarriageData.getOnlinePlayer(partnerUuid)

        val chatMsg = Component.literal("")
            .append(Component.literal("❤ [Private] ").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(player.name.string).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE))

        player.sendSystemMessage(chatMsg)
        if (partner == null) {
            player.sendSystemMessage(Msg.info("($partnerName is offline — message not delivered)"))
        } else {
            partner.sendSystemMessage(chatMsg)
        }
        return 1
    }

    // ─── /marry kiss ─────────────────────────────────────────────────────────

    fun kissCommand(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        val partner = MarriageData.getOnlinePlayer(partnerUuid)
        if (partner == null) {
            player.sendSystemMessage(Msg.error("$partnerName is not online."))
            return 0
        }
        spawnKissEffects(player, partner)
        return 1
    }

    fun spawnKissEffects(player: ServerPlayer, partner: ServerPlayer) {
        val partnerName = partner.name.string
        val playerName = player.name.string

        listOf(player, partner).forEach { p ->
            (p.level() as ServerLevel).sendParticles(
                ParticleTypes.HEART,
                p.x, p.y + 2.2, p.z,
                8, 0.3, 0.3, 0.3, 0.05
            )
        }

        partner.displayClientMessage(
            Component.literal("❤ $playerName kissed you! ❤").withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        )
        player.displayClientMessage(
            Component.literal("❤ You kissed $partnerName! ❤").withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        )

        (player.level() as ServerLevel).playSound(
            null,
            player.blockPosition(),
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS,
            0.5f,
            1.8f
        )
    }

    // ─── /marry hug ──────────────────────────────────────────────────────────

    private fun hug(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        val partner = MarriageData.getOnlinePlayer(partnerUuid)
        if (partner == null) {
            player.sendSystemMessage(Msg.error("$partnerName is not online."))
            return 0
        }

        partner.displayClientMessage(
            Component.literal("🤗 ${player.name.string} hugged you!").withStyle(ChatFormatting.YELLOW),
            true
        )
        player.displayClientMessage(
            Component.literal("🤗 You hugged $partnerName!").withStyle(ChatFormatting.YELLOW),
            true
        )

        (player.level() as ServerLevel).sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            player.x, player.y + 1.0, player.z,
            12, 0.5, 0.5, 0.5, 0.05
        )
        return 1
    }

    private fun sex(source: CommandSourceStack): Int {
        val cfg = ModConfig.values
        val player = source.getPlayerOrException()

        if (!cfg.enableAdultCommands) {
            player.sendSystemMessage(Msg.info("This command is disabled by server config."))
            return 0
        }
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }

        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!
        val partner = MarriageData.getOnlinePlayer(partnerUuid)
        if (partner == null) {
            player.sendSystemMessage(Msg.error("$partnerName is not online."))
            return 0
        }

        val now = Instant.now().epochSecond
        val cooldown = cfg.sexCommandCooldownSeconds.coerceAtLeast(0)
        val key = player.getStringUUID()
        val last = sexCooldowns[key] ?: 0L
        if (now - last < cooldown) {
            val remaining = cooldown - (now - last)
            player.sendSystemMessage(Msg.info("Wait ${remaining}s before using this again."))
            return 0
        }
        sexCooldowns[key] = now

        val level = player.level() as ServerLevel
        level.sendParticles(ParticleTypes.HEART, player.x, player.y + 1.0, player.z, 20, 0.6, 0.5, 0.6, 0.02)
        level.sendParticles(ParticleTypes.HEART, partner.x, partner.y + 1.0, partner.z, 20, 0.6, 0.5, 0.6, 0.02)
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.2f)

        partner.displayClientMessage(
            Component.literal("❤ ${player.name.string} had sex with you (;.").withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        )
        player.displayClientMessage(
            Component.literal("❤ You had sex with $partnerName.").withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        )
        return 1
    }

    // ─── /marry pvpon/off ────────────────────────────────────────────────────

    private fun setPvp(source: CommandSourceStack, protect: Boolean): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        MarriageData.setPvpProtected(player.getStringUUID(), protect)
        val status = if (protect) "OFF (protected)" else "ON"
        player.sendSystemMessage(Msg.info("PvP with your partner is now $status."))
        return 1
    }

    // ─── /marry seen ─────────────────────────────────────────────────────────

    private fun seen(source: CommandSourceStack): Int {
        val player = source.getPlayerOrException()
        if (!MarriageData.isMarried(player.getStringUUID())) {
            player.sendSystemMessage(Msg.error("You are not married."))
            return 0
        }
        val partnerUuid = MarriageData.getPartnerUuid(player.getStringUUID())!!
        val partnerName = MarriageData.getPartnerName(player.getStringUUID())!!

        val online = MarriageData.getOnlinePlayer(partnerUuid)
        if (online != null) {
            player.sendSystemMessage(Msg.heart("$partnerName is currently online!"))
        } else {
            val lastSeen = MarriageData.getLastSeen(partnerUuid)
            if (lastSeen == null) {
                player.sendSystemMessage(Msg.info("$partnerName has never been seen online."))
            } else {
                val formatted = DATE_FMT.format(Instant.ofEpochSecond(lastSeen))
                player.sendSystemMessage(Msg.info("$partnerName was last seen on $formatted."))
            }
        }
        return 1
    }

    // ─── /marry list ─────────────────────────────────────────────────────────

    private fun list(source: CommandSourceStack): Int {
        val couples = MarriageData.getAllCouples()
        if (couples.isEmpty()) {
            source.sendSuccess({ Msg.info("No couples are registered yet.") }, false)
            return 1
        }
        source.sendSuccess({
            val text = Component.literal("━━━ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("❤ Married Couples ❤").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(" ━━━\n").withStyle(ChatFormatting.DARK_GRAY))
            couples.forEachIndexed { i, record ->
                text.append(
                    Component.literal("  ${i + 1}. ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(record.player1Name).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" ❤ ").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(record.player2Name).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                )
            }
            text
        }, false)
        return 1
    }
}