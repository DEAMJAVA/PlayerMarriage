package net.deamjava.player_marriage.mixin;

import net.deamjava.player_marriage.events.PvpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void playerMarriage_cancelPartnerDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (PvpHandler.INSTANCE.shouldCancelDamage(self, source)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
