package icu.tatyrealms.tatykefei;

import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import icu.tatyrealms.tatykefei.item.TSFItems;
import icu.tatyrealms.tatykefei.item.ProtectionTotems;
import icu.tatyrealms.tatykefei.compat.trinkets.TrinketsCompat;
import icu.tatyrealms.tatykefei.compat.carbonchat.CarbonChatCompat;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureCompat;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureHypnosis;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureTanks;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureBodies;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureSkins;
import icu.tatyrealms.tatykefei.command.GiveBodyCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.ActionPayload;
import icu.tatyrealms.tatykefei.network.AnimationPayload;
import icu.tatyrealms.tatykefei.network.StatePayload;
import icu.tatyrealms.tatykefei.network.SettingsPayload;
import icu.tatyrealms.tatykefei.network.BodyPickingPayload;
import icu.tatyrealms.tatykefei.network.ObserverPayload;
import icu.tatyrealms.tatykefei.network.SkinLayersPayload;
import icu.tatyrealms.tatykefei.network.HypnosisProgressPayload;
import icu.tatyrealms.tatykefei.network.HypnosisSpeechPayload;
import icu.tatyrealms.tatykefei.network.BodyDragPayload;
import icu.tatyrealms.tatykefei.state.HypnosisRestrictions;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.persistence.StorageLoginTask;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import icu.tatyrealms.tatykefei.block.BodyDisplayBlock;
import icu.tatyrealms.tatykefei.block.BodyDisplayBlockEntity;
import icu.tatyrealms.tatykefei.network.BodyDisplayPayload;
import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import icu.tatyrealms.tatykefei.state.Form;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class TSF implements ModInitializer {
    public static final String MOD_ID = "tsf";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        TSFItems.initialize();
        BodyDisplayBlockEntity.initialize();
        TrinketsCompat.initialize();
        CarbonChatCompat.initialize();
        NeedsOfNatureCompat.initialize();
        NeedsOfNatureHypnosis.initialize();
        NeedsOfNatureTanks.initialize();
        NeedsOfNatureBodies.initialize();
        NeedsOfNatureSkins.initialize();
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnimationPayload.TYPE, AnimationPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverPayload.TYPE, ObserverPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkinLayersPayload.TYPE, SkinLayersPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HypnosisProgressPayload.TYPE, HypnosisProgressPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HypnosisSpeechPayload.TYPE, HypnosisSpeechPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BodyDragPayload.TYPE, BodyDragPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BodyDisplayPayload.TYPE, BodyDisplayPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BodyDisplayPayload.TYPE, BodyDisplayPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BodyPickingPayload.TYPE, BodyPickingPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) -> {
            ServerPlayNetworking.send(context.player(), settings(context.player(), payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE,
                (payload, context) -> BodyManager.get().handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(HypnosisSpeechPayload.TYPE,
                (payload, context) -> BodyManager.get().speakAsHypnotized(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BodyDragPayload.TYPE,
                (payload, context) -> BodyManager.get().dragBody(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BodyDisplayPayload.TYPE,
                (payload, context) -> BodyManager.get().configureDisplay(context.player(), payload));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                TSFServerConfig.reload();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Unable to load TSF server settings", exception);
            }
            new BodyManager(server);
        });
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> BodyManager.get().save(flush));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BodyManager.clear();
            ProtectionTotems.clear();
        });
        Identifier chatPhase = id("chat_restrictions");
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(chatPhase, Event.DEFAULT_PHASE);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(chatPhase, (message, sender, parameters) ->
                !BodyManager.restricted(sender, HypnosisRestrictions.SPEECH) && BodyManager.get().allowChat(sender, message));
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, parameters) ->
                !(source.getEntity() instanceof Player player)
                        || !BodyManager.restricted(player, HypnosisRestrictions.SPEECH)
                        && (!(player instanceof ServerPlayer serverPlayer) || BodyManager.get().allowCommandMessage(serverPlayer)));
        ServerTickEvents.END_SERVER_TICK.register(server -> BodyManager.get().tick());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> BodyManager.get().entityLoaded(entity));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StorageLoginTask.entered(handler.player.getUUID());
            BodyManager.get().joined(handler.player);
            ServerPlayNetworking.send(handler.player, new BodyPickingPayload(TSFServerConfig.get().bodyPosePicking));
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) ->
                ((FabricServerConfigurationPacketListenerImpl) handler).addTask(new StorageLoginTask(handler)));
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            StorageLoginTask.disconnected(handler);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Player player && BodyManager.form(player) == Form.SPIRIT)
                        && !BodyManager.emptyBody(entity));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                BodyManager.get().died(player);
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                BodyManager.get().beforeDeath(player);
            }
            return true;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> BodyManager.get().respawned(newPlayer));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (BodyManager.restricted(player, HypnosisRestrictions.INTERACTION)) {
                return InteractionResult.FAIL;
            }
            if (player.isShiftKeyDown() && entity instanceof EmptyBodyHolder holder && holder.tsf$isMounted()) {
                if (player instanceof ServerPlayer actor && hand == InteractionHand.MAIN_HAND) {
                    BodyManager.get().interactBody(actor, entity);
                }
                return InteractionResult.SUCCESS;
            }
            if (entity instanceof Player && player.getItemInHand(hand).is(TSFItems.PHONE)) {
                return TSFItems.PHONE.use(level, player, hand);
            }
            if (entity instanceof Player && player.getItemInHand(hand).is(TSFItems.BODY_REGENERATION_TOTEM)) {
                if (player instanceof ServerPlayer actor && entity instanceof ServerPlayer target) {
                    BodyManager.get().regenerateBody(actor, target, player.getItemInHand(hand));
                }
                return InteractionResult.SUCCESS;
            }
            if (BodyManager.spiritSpear(player.getItemInHand(hand))) {
                return player.getItemInHand(hand).getItem().use(level, player, hand);
            }
            if (entity instanceof Player target && BodyManager.form(target) == Form.SLIME
                    && player.getItemInHand(hand).is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
                if (player instanceof ServerPlayer serverPlayer && target instanceof ServerPlayer targetPlayer) {
                    BodyManager.get().bottleSlime(serverPlayer, targetPlayer, hand);
                }
                return InteractionResult.SUCCESS;
            }
            if (entity instanceof Player target && BodyManager.form(target) == Form.SLIME
                    && player.getItemInHand(hand).has(net.minecraft.core.component.DataComponents.DYE)) {
                if (player instanceof ServerPlayer serverPlayer && target instanceof ServerPlayer targetPlayer) {
                    BodyManager.get().dyeSlime(serverPlayer, targetPlayer, player.getItemInHand(hand));
                }
                return InteractionResult.SUCCESS;
            }
            if (BodyManager.emptyBody(entity)) {
                if (entity instanceof net.minecraft.world.entity.Leashable leashable && leashable.canBeLeashed()
                        && (player.getItemInHand(hand).is(net.minecraft.world.item.Items.LEAD)
                        || player.getItemInHand(hand).is(net.minecraft.world.item.Items.SHEARS))) {
                    return entity.interact(player, hand, hit == null ? entity.position() : hit.getLocation());
                }
                if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
                    BodyManager.get().interactBody(serverPlayer, entity);
                }
                return InteractionResult.SUCCESS;
            }
            if (entity instanceof Player && hand == InteractionHand.MAIN_HAND) {
                if (player.getMainHandItem().is(TSFItems.SKIN_KNIFE)) {
                    if (player instanceof ServerPlayer serverPlayer && entity instanceof ServerPlayer target) {
                        BodyManager.get().skinPlayer(serverPlayer, target);
                    }
                    return InteractionResult.SUCCESS;
                }
                if ((player.getMainHandItem().isEmpty() || BodyManager.form(player) != Form.HUMAN)
                        && player instanceof ServerPlayer serverPlayer && entity instanceof ServerPlayer target
                        && BodyManager.get().enterZipper(serverPlayer, target)) {
                    return InteractionResult.SUCCESS;
                }
                if (BodyManager.form(player) == Form.SLIME) {
                    if (player instanceof ServerPlayer serverPlayer && entity instanceof ServerPlayer target) {
                        BodyManager.get().handle(serverPlayer, new ActionPayload(Action.POSSESS, target.getUUID().toString()));
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return BodyManager.form(player) == Form.SPIRIT ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (BodyManager.restricted(player, HypnosisRestrictions.INTERACTION)) {
                return InteractionResult.FAIL;
            }
            if (entity instanceof EmptyBodyHolder holder && holder.tsf$isMounted()) {
                if (player.isShiftKeyDown() && player instanceof ServerPlayer actor && hand == InteractionHand.MAIN_HAND) {
                    BodyManager.get().pickUpDisplayedBody(actor, entity);
                }
                return InteractionResult.SUCCESS;
            }
            if (BodyManager.spiritSpear(player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            if (BodyManager.foldedSkin(entity)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    BodyManager.get().hitSkin(serverPlayer, entity);
                }
                return InteractionResult.SUCCESS;
            }
            if (BodyManager.restricted(player, HypnosisRestrictions.INTERACTION) || BodyManager.form(player) == Form.SPIRIT
                    || BodyManager.emptyBody(entity)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (BodyManager.restricted(player, HypnosisRestrictions.INTERACTION)) {
                return InteractionResult.FAIL;
            }
            if (BodyManager.form(player) == Form.SLIME
                    && player.getItemInHand(hand).has(net.minecraft.core.component.DataComponents.DYE)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    BodyManager.get().dyeSlime(serverPlayer, serverPlayer, player.getItemInHand(hand));
                }
                return InteractionResult.SUCCESS;
            }
            if (BodyManager.form(player) == Form.SPIRIT && !player.getItemInHand(hand).is(TSFItems.PHONE)
                    && !player.getItemInHand(hand).is(TSFItems.SKIN_SUIT)
                    && !BodyManager.spiritSpear(player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isShiftKeyDown() && level.getBlockState(hit.getBlockPos()).is(BodyDisplayBlock.INSTANCE)) {
                if (!restricted(player) && player instanceof ServerPlayer actor && hand == InteractionHand.MAIN_HAND) {
                    BodyManager.get().interactDisplay(actor, hit.getBlockPos(), hand);
                }
                return InteractionResult.SUCCESS;
            }
            if (BodyManager.spiritSpear(player.getItemInHand(hand))) {
                return player.getItemInHand(hand).getItem().use(level, player, hand);
            }
            if (BodyManager.form(player) == Form.SPIRIT
                    && !BodyManager.restricted(player, HypnosisRestrictions.INTERACTION)
                    && level.getBlockState(hit.getBlockPos()).is(BodyDisplayBlock.INSTANCE)) {
                if (player instanceof ServerPlayer actor) {
                    BodyManager.get().interactDisplay(actor, hit.getBlockPos(), hand);
                }
                return InteractionResult.SUCCESS;
            }
            return restricted(player) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
            if (player.isShiftKeyDown() && level.getBlockState(position).is(BodyDisplayBlock.INSTANCE)) {
                if (!restricted(player) && player instanceof ServerPlayer actor && hand == InteractionHand.MAIN_HAND) {
                    BodyManager.get().pickUpDisplayedBody(actor, position);
                }
                return InteractionResult.SUCCESS;
            }
            return restricted(player) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, position, state, blockEntity) -> !restricted(player)
                && !(player.isShiftKeyDown() && state.is(BodyDisplayBlock.INSTANCE)));
        registerCommands();
    }

    private static boolean restricted(Player player) {
        return BodyManager.restricted(player, HypnosisRestrictions.INTERACTION) || BodyManager.form(player) == Form.SPIRIT;
    }

    public static SettingsPayload settings(ServerPlayer player, SettingsPayload payload) {
        boolean editable = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || player.level().getServer().isSingleplayerOwner(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
        if (!payload.json().isEmpty() && editable) {
            try {
                TSFServerConfig.save(TSFServerConfig.parse(payload.json()));
                synchronizeBodyPicking(player.level().getServer());
                BodyManager.message(player, "config_saved");
            } catch (java.io.IOException exception) {
                player.sendSystemMessage(Component.literal(exception.getMessage()));
            }
        }
        return new SettingsPayload(TSFServerConfig.get().json(), editable);
    }

    private static void synchronizeBodyPicking(net.minecraft.server.MinecraftServer server) {
        BodyPickingPayload payload = new BodyPickingPayload(TSFServerConfig.get().bodyPosePicking);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tsf")
                        .executes(context -> {
                            BodyManager.get().openPhone(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.literal("return").executes(context -> {
                            BodyManager.get().handle(context.getSource().getPlayerOrException(), new ActionPayload(Action.RETURN, ""));
                            return 1;
                        }))
                        .then(Commands.literal("color")
                                .then(Commands.argument("hex", StringArgumentType.word()).executes(context -> {
                                    BodyManager.get().handle(context.getSource().getPlayerOrException(),
                                            new ActionPayload(Action.SLIME_COLOR, StringArgumentType.getString(context, "hex")));
                                    return 1;
                                })))
                        .then(Commands.literal("undress").executes(context -> {
                            BodyManager.get().handle(context.getSource().getPlayerOrException(), new ActionPayload(Action.UNDRESS, ""));
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(context -> {
                            BodyManager.get().handle(context.getSource().getPlayerOrException(), new ActionPayload(Action.LEAVE_ZIPPER, ""));
                            return 1;
                        }))
                        .then(Commands.literal("restore").executes(context -> {
                            BodyManager.get().handle(context.getSource().getPlayerOrException(), new ActionPayload(Action.RESTORE_SKIN, ""));
                            return 1;
                        }))
                        .then(Commands.literal("config")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.literal("reload").executes(context -> {
                                    try {
                                        TSFServerConfig.reload();
                                        synchronizeBodyPicking(context.getSource().getServer());
                                        context.getSource().sendSuccess(() -> Component.translatable("message.tsf.config_reloaded"), true);
                                        return 1;
                                    } catch (java.io.IOException exception) {
                                        context.getSource().sendFailure(Component.literal(exception.getMessage()));
                                        return 0;
                                    }
                                })))
                        .then(Commands.literal("kit")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    for (var item : new net.minecraft.world.item.Item[]{TSFItems.PHONE, TSFItems.SKIN_KNIFE,
                                            TSFItems.BODY_REGENERATION_TOTEM, TSFItems.SOUL_PROJECTION_POTION,
                                            TSFItems.PERSONALITY_EXPULSION_POTION, TSFItems.ZIPPER}) {
                                        ItemStack stack = new ItemStack(item);
                                        if (!player.getInventory().add(stack)) {
                                            player.drop(stack, false, Prediction.SERVER_ONLY);
                                        }
                                    }
                                    return 1;
                                }))
                        .then(GiveBodyCommand.create())
                        .then(Commands.literal("recover")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                                    BodyManager.get().recover(context.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                                    BodyManager.get().recover(EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                })))));
    }
}
