package icu.tatyrealms.tatykefei.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BodyPhysics {
    private BodyPhysics() {
    }

    public static boolean isClear(Level level, AABB bounds) {
        return level.getWorldBorder().isWithinBounds(bounds) && level.noBlockCollision(null, bounds.deflate(1.0E-7));
    }

    public static Vec3 findPosition(Level level, Vec3 origin, EntityDimensions dimensions) {
        if (isClear(level, dimensions.makeBoundingBox(origin))) {
            return origin;
        }
        Vec3 center = Vec3.atBottomCenterOf(BlockPos.containing(origin));
        List<Vec3> candidates = new ArrayList<>();
        for (int rise = 0; rise <= 2; rise++) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                    candidates.add(new Vec3(center.x + offsetX, origin.y + rise, center.z + offsetZ));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(origin)));
        for (Vec3 candidate : candidates) {
            if (isClear(level, dimensions.makeBoundingBox(candidate))) {
                return candidate;
            }
        }
        return null;
    }
}
