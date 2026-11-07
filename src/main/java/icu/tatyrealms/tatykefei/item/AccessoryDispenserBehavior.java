package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public final class AccessoryDispenserBehavior extends DefaultDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        var destination = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        for (ServerPlayer player : source.level().getEntitiesOfClass(ServerPlayer.class, new AABB(destination),
                candidate -> candidate.isAlive() && !candidate.isSpectator())) {
            boolean equipped = stack.is(TSFItems.SKIN_SUIT) ? BodyManager.get().wearSkin(player, stack)
                    : BodyManager.get().equipZipper(player, stack);
            if (equipped) {
                return stack;
            }
        }
        if (stack.is(TSFItems.ZIPPER)) {
            for (var entity : source.level().getEntitiesOfClass(Entity.class, new AABB(destination), BodyManager::emptyBody)) {
                if (BodyManager.get().equipBodyZipper(entity, stack)) {
                    return stack;
                }
            }
        }
        return super.execute(source, stack);
    }
}
