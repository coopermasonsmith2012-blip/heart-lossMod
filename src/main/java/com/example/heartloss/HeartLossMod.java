package com.example.heartloss;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class HeartLossMod implements ModInitializer {
    public static final String MOD_ID = "heartloss";

    // This creates the physical heart item that players can hold and use
    public static final Item HEART_ITEM = new Item(new Item.Settings().maxCount(1)) {
        @Override
        public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (!world.isClient && user instanceof ServerPlayerEntity player) {
                EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                
                if (maxHealthAttr != null) {
                    double currentMax = maxHealthAttr.getBaseValue();
                    // Hard limit at 25 hearts (50 health points)
                    if (currentMax < 50.0) {
                        maxHealthAttr.setBaseValue(currentMax + 2.0); // Add 1 heart
                        player.heal(2.0f);
                        stack.decrement(1);
                        return TypedActionResult.consume(stack);
                    } else {
                        player.sendMessage(Text.literal("You already have the maximum of 25 hearts!"), true);
                    }
                }
            }
            return TypedActionResult.pass(stack);
        }
    };

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "heart"), HEART_ITEM);

        // Rule: Start new players with 10 hearts
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null && maxHealthAttr.getBaseValue() == 20.0) {
                maxHealthAttr.setBaseValue(20.0);
            }
        });

        // Rule: Handle death, drop indestructible hearts, enforce 3-heart minimum
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            EntityAttributeInstance oldMaxHealth = oldPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            EntityAttributeInstance newMaxHealth = newPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

            if (oldMaxHealth != null && newMaxHealth != null) {
                double currentMax = oldMaxHealth.getBaseValue();
                
                // Safety net: Cannot go below 3 hearts (6 health points)
                if (currentMax <= 6.0) {
                    newMaxHealth.setBaseValue(6.0);
                    newPlayer.setHealth(6.0f);
                    oldPlayer.sendMessage(Text.literal("You are at the 3-heart minimum protection limit. No heart dropped."), false);
                    return;
                }

                // Reduce max health by 1 heart
                double lostHealth = currentMax - 2.0;
                newMaxHealth.setBaseValue(lostHealth);
                newPlayer.setHealth((float) lostHealth);

                // Spawn the heart item on the ground where they died
                World world = oldPlayer.getWorld();
                ItemEntity heartDrop = new ItemEntity(world, oldPlayer.getX(), oldPlayer.getY(), oldPlayer.getZ(), new ItemStack(HEART_ITEM));
                
                // Make the item immune to explosions/lava (indestructible)
                heartDrop.setInvulnerable(true); 
                world.spawnEntity(heartDrop);
            }
        });

        // Rule: Instant Villager Restocks on right-click
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && entity instanceof VillagerEntity villager) {
                villager.getOffers().forEach(tradeOffer -> tradeOffer.resetUses());
            }
            return ActionResult.PASS;
        });
    }
}
