package icu.tatyrealms.tatykefei.mixin.entity;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mannequin.class)
public abstract class MannequinMixin implements EmptyBodyHolder, Leashable {
    @Unique
    private LeashData tsf$leashData;

    @Override
    public LeashData getLeashData() {
        return tsf$leashData;
    }

    @Override
    public void setLeashData(LeashData leashData) {
        tsf$leashData = leashData;
    }

    @Override
    public boolean canBeLeashed() {
        return tsf$isEmptyBody() && !tsf$isFoldedSkin() && !tsf$isMounted();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void tsf$saveLeash(ValueOutput output, CallbackInfo callback) {
        writeLeashData(output, tsf$leashData);
    }

    @Unique
    private static final EntityDataAccessor<Boolean> TSF_BODY = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Boolean> TSF_SKIN = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<String> TSF_RIG = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.STRING);
    @Unique
    private static final EntityDataAccessor<Boolean> TSF_SHOW_NAME = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Boolean> TSF_POSEABLE = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Boolean> TSF_SHOW_CAPE = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<String> TSF_FACE = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.STRING);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void tsf$defineData(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(TSF_BODY, false);
        builder.define(TSF_SKIN, false);
        builder.define(TSF_RIG, "");
        builder.define(TSF_SHOW_NAME, false);
        builder.define(TSF_POSEABLE, true);
        builder.define(TSF_SHOW_CAPE, true);
        builder.define(TSF_FACE, "");
    }

    @Override
    public boolean tsf$showDisplayName() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_SHOW_NAME);
    }

    @Override
    public boolean tsf$poseable() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_POSEABLE);
    }

    @Override
    public boolean tsf$showCape() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_SHOW_CAPE);
    }

    @Override
    public String tsf$face() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_FACE);
    }

    @Override
    public void tsf$setDisplayOptions(boolean showName, boolean poseable, boolean showCape, String face) {
        var data = ((Mannequin) (Object) this).getEntityData();
        data.set(TSF_SHOW_NAME, showName);
        data.set(TSF_POSEABLE, poseable);
        data.set(TSF_SHOW_CAPE, showCape);
        data.set(TSF_FACE, face == null ? "" : face);
    }

    @Override
    public String tsf$rigPose() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_RIG);
    }

    @Override
    public void tsf$setRigPose(String pose) {
        ((Mannequin) (Object) this).getEntityData().set(TSF_RIG, pose);
    }

    @Override
    public boolean tsf$isEmptyBody() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_BODY);
    }

    @Override
    public void tsf$setEmptyBody(boolean emptyBody) {
        Mannequin mannequin = (Mannequin) (Object) this;
        mannequin.getEntityData().set(TSF_BODY, emptyBody);
        mannequin.refreshDimensions();
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tsf$load(ValueInput input, CallbackInfo callback) {
        tsf$setEmptyBody(((Mannequin) (Object) this).entityTags().contains(BodyManager.BODY_TAG));
        tsf$setFoldedSkin(((Mannequin) (Object) this).entityTags().contains(BodyManager.SKIN_TAG));
        readLeashData(input);
    }

    @Override
    public boolean tsf$isFoldedSkin() {
        return ((Mannequin) (Object) this).getEntityData().get(TSF_SKIN);
    }

    @Override
    public void tsf$setFoldedSkin(boolean foldedSkin) {
        Mannequin mannequin = (Mannequin) (Object) this;
        mannequin.getEntityData().set(TSF_SKIN, foldedSkin);
        mannequin.refreshDimensions();
    }
}
