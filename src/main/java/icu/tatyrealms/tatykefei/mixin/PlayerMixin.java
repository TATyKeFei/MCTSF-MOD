package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.FormHolder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin implements FormHolder {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void tsf$displayIdentity(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof ServerPlayer player && BodyManager.get() != null) {
            Component name = BodyManager.get().assumedName(player);
            if (name != null) {
                callback.setReturnValue(name);
            }
        }
    }

    @Unique
    private static final EntityDataAccessor<Integer> TSF_FORM = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
    @Unique
    private static final EntityDataAccessor<Boolean> TSF_HYPNOTIZED = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Integer> TSF_HYPNOSIS_RESTRICTIONS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void tsf$defineData(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(TSF_FORM, Form.HUMAN.ordinal());
        builder.define(TSF_HYPNOTIZED, false);
        builder.define(TSF_HYPNOSIS_RESTRICTIONS, 0);
    }

    @Override
    public Form tsf$getForm() {
        return Form.values()[((Player) (Object) this).getEntityData().get(TSF_FORM)];
    }

    @Override
    public void tsf$setForm(Form form) {
        Player player = (Player) (Object) this;
        player.getEntityData().set(TSF_FORM, form.ordinal());
        player.refreshDimensions();
    }

    @Override
    public boolean tsf$isHypnotized() {
        return ((Player) (Object) this).getEntityData().get(TSF_HYPNOTIZED);
    }

    @Override
    public void tsf$setHypnotized(boolean hypnotized) {
        ((Player) (Object) this).getEntityData().set(TSF_HYPNOTIZED, hypnotized);
    }

    @Override
    public int tsf$hypnosisRestrictions() {
        return ((Player) (Object) this).getEntityData().get(TSF_HYPNOSIS_RESTRICTIONS);
    }

    @Override
    public void tsf$setHypnosisRestrictions(int restrictions) {
        ((Player) (Object) this).getEntityData().set(TSF_HYPNOSIS_RESTRICTIONS, restrictions);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void tsf$spiritPhysics(CallbackInfo callback) {
        if (tsf$getForm() == Form.SPIRIT) {
            Player player = (Player) (Object) this;
            player.noPhysics = true;
            player.setOnGround(false);
        }
    }

    @Inject(method = "touch", at = @At("HEAD"), cancellable = true)
    private void tsf$spiritCannotCollect(Entity entity, CallbackInfo callback) {
        if (tsf$getForm() == Form.SPIRIT || BodyManager.restricted((Player) (Object) this,
                icu.tatyrealms.tatykefei.state.HypnosisRestrictions.INTERACTION)) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tsf$refreshForm(CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (tsf$getForm() == Form.SLIME && player.getBbHeight() > 0.6F) {
            player.refreshDimensions();
        } else if (tsf$getForm() != Form.SLIME && player.getBbHeight() < 0.6F && !player.isSleeping()) {
            player.refreshDimensions();
        }
        if (tsf$getForm() == Form.SPIRIT) {
            player.noPhysics = true;
            player.fallDistance = 0;
        }
    }
}
