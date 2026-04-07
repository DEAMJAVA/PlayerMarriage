package net.deamjava.player_marriage.events

import net.deamjava.player_marriage.data.MarriageData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

object PvpHandler {

    fun register() {
        // We use a mixin for damage cancellation (see PlayerEntityMixin.java)
        // This object exists so we can expose the check logic cleanly
    }

    /**
     * Called from the mixin. Returns true if the attack should be cancelled.
     */
    fun shouldCancelDamage(victim: ServerPlayer, source: DamageSource): Boolean {
        val attacker = source.entity
        if (attacker !is ServerPlayer) return false
        if (!MarriageData.isMarried(victim.stringUUID)) return false

        val partnerUuid = MarriageData.getPartnerUuid(victim.stringUUID) ?: return false
        if (partnerUuid != attacker.stringUUID) return false

        // Cancel only if BOTH players have pvp protection toggled
        return MarriageData.isPvpProtected(victim.stringUUID) &&
               MarriageData.isPvpProtected(attacker.stringUUID)
    }
}
