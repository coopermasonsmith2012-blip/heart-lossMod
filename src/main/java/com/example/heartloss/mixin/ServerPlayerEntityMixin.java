package com.example.heartloss.mixin;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readPersistentHeartData(NbtCompound nbt, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        
        if (maxHealthAttr != null) {
            if (nbt.contains("CustomMaxHeartPool", 6)) { // 6 denotes double type tag
                maxHealthAttr.setBaseValue(nbt.getDouble("CustomMaxHeartPool"));
            } else {
                // If completely new player data file, initialize at 10 hearts (20.0 HP)
                maxHealthAttr.setBaseValue(20.0);
            }
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writePersistentHeartData(NbtCompound nbt, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        
        if (maxHealthAttr != null) {
            nbt.putDouble("CustomMaxHeartPool", maxHealthAttr.getBaseValue());
        }
    }
}