package com.example.heartloss;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class HeartLossMod implements ModInitializer {
    public static final String MOD_ID = "heartloss";

    public static final Item HEART_ITEM = new Item(new Item.Settings().maxCount(1)) {
        @Override
        public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (!world.isClient && user instanceof ServerPlayerEntity player) {
                EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                
                if (maxHealthAttr != null) {
                    double currentMax = maxHealthAttr.getBaseValue();
                    if (currentMax < 50.0) {
                        maxHealthAttr.setBaseValue(currentMax + 2.0);
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

        // /withdraw command for 26.1.2
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("withdraw")
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        int amountToWithdraw = IntegerArgumentType.getInteger(context, "amount");
                        
                        EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                        if (maxHealthAttr == null) return 0;

                        double currentMaxHealth = maxHealthAttr.getBaseValue();
                        double healthToRemove = amountToWithdraw * 2.0;
                        double newMaxHealth = currentMaxHealth - healthToRemove;

                        if (newMaxHealth < 6.0) {
                            int maxPossibleWithdraw = (int) ((currentMaxHealth - 6.0) / 2.0);
                            if (maxPossibleWithdraw <= 0) {
                                context.getSource().sendError(Text.literal("You are at the 3-heart protection limit!"));
                            } else {
                                context.getSource().sendError(Text.literal("Max withdraw right now is " + maxPossibleWithdraw));
                            }
                            return 0;
                        }

                        maxHealthAttr.setBaseValue(newMaxHealth);
                        if (player.getHealth() > newMaxHealth) {
                            player.setHealth((float) newMaxHealth);
                        }

                        for (int i = 0; i < amountToWithdraw; i++) {
                            ItemStack heartStack = new ItemStack(HEART_ITEM);
                            if (!player.getInventory().insertStack(heartStack)) {
                                player.dropItem(heartStack, false);
                            }
                        }
                        return 1;
                    })
                )
            );
        });

        // Initialize health attributes on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null && maxHealthAttr.getBaseValue() == 20.0) {
                maxHealthAttr.setBaseValue(20.0);
            }
        });

        // Drop logic on respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            EntityAttributeInstance oldMaxHealth = oldPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            EntityAttributeInstance newMaxHealth = newPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

            if (oldMaxHealth != null && newMaxHealth != null) {
                double currentMax = oldMaxHealth.getBaseValue();
                if (currentMax <= 6.0) {
                    newMaxHealth.setBaseValue(6.0);
                    newPlayer.setHealth(6.0f);
                    return;
                }

                double lostHealth = currentMax - 2.0;
                newMaxHealth.setBaseValue(lostHealth);
                newPlayer.setHealth((float) lostHealth);

                World world = oldPlayer.getWorld();
                ItemEntity heartDrop = new ItemEntity(world, oldPlayer.getX(), oldPlayer.getY(), oldPlayer.getZ(), new ItemStack(HEART_ITEM));
                heartDrop.setInvulnerable(true); 
                world.spawnEntity(heartDrop);
            }
        });
    }
}
