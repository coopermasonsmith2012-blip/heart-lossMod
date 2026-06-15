package com.example.heartloss;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
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
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;

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
                    // Enforce the strict 15-heart ceiling (30.0 HP)
                    if (currentMax < 30.0) { 
                        maxHealthAttr.setBaseValue(currentMax + 2.0);
                        player.heal(2.0f);
                        stack.decrement(1);
                        return TypedActionResult.consume(stack);
                    } else {
                        player.sendMessage(Text.literal("§cYou cannot use hearts if you are at or above 15 hearts!"), true);
                    }
                }
            }
            return TypedActionResult.pass(stack);
        }
    };

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "heart"), HEART_ITEM);

        // Continuous Tick Monitor: Scans open container slots every tick to stop stashing
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                handleInventoryValidation(player);
            }
        });

        // /withdraw Command Tracker
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

                        // 3 Hearts Minimum rule protection (6.0 HP)
                        if (newMaxHealth < 6.0) {
                            int maxPossibleWithdraw = (int) ((currentMaxHealth - 6.0) / 2.0);
                            if (maxPossibleWithdraw <= 0) {
                                context.getSource().sendError(Text.literal("§cYou are at the 3-heart protection limit!"));
                            } else {
                                context.getSource().sendError(Text.literal("§cMax withdraw right now is " + maxPossibleWithdraw));
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
                        context.getSource().sendFeedback(() -> Text.literal("§aWithdrew " + amountToWithdraw + " heart(s)!"), false);
                        return 1;
                    })
                )
            );
        });

        // Safe Data Bridge: Transfers calculated health properties during respawn reconstruction
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                EntityAttributeInstance oldMax = oldPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                EntityAttributeInstance newMax = newPlayer.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

                if (oldMax != null && newMax != null) {
                    double currentMax = oldMax.getBaseValue();
                    
                    if (currentMax <= 6.0) {
                        newMax.setBaseValue(6.0);
                        return;
                    }

                    // Process drop and calculate penalty
                    double lostHealth = currentMax - 2.0;
                    newMax.setBaseValue(lostHealth);

                    World world = oldPlayer.getWorld();
                    ItemEntity heartDrop = new ItemEntity(world, oldPlayer.getX(), oldPlayer.getY(), oldPlayer.getZ(), new ItemStack(HEART_ITEM));
                    heartDrop.setInvulnerable(true); 
                    world.spawnEntity(heartDrop);
                }
            }
        });
    }

    public static void handleInventoryValidation(ServerPlayerEntity player) {
        if (player.currentScreenHandler instanceof PlayerScreenHandler) {
            return;
        }

        for (Slot slot : player.currentScreenHandler.slots) {
            Inventory inventory = slot.inventory;
            if (inventory != player.getInventory()) {
                ItemStack stackInSlot = slot.getStack();
                if (stackInSlot.isOf(HEART_ITEM)) {
                    slot.setStack(ItemStack.EMPTY);
                    if (!player.getInventory().insertStack(stackInSlot)) {
                        player.dropItem(stackInSlot, false);
                    }
                    player.sendMessage(Text.literal("§cYou cannot stash hearts in containers or shelves!"), true);
                    player.currentScreenHandler.sendContentUpdates();
                }
            }
        }
    }
}