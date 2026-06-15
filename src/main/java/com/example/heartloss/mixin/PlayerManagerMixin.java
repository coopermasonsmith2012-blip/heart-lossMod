package com.example.heartloss.mixin;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "respawn", at = @At("RETURN"))
    private void verifyHealthPostRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity targetPlayer = cir.getReturnValue();
        if (targetPlayer != null) {
            EntityAttributeInstance oldMaxAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            EntityAttributeInstance newMaxAttr = targetPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

            if (oldMaxAttr != null && newMaxAttr != null) {
                // Read what the copy handler calculated and force overwrite vanilla's automatic reset
                double expectedHealth = newMaxAttr.getBaseValue();
                newMaxAttr.setBaseValue(expectedHealth);
                targetPlayer.setHealth((float) expectedHealth);
            }
        }
    }
}