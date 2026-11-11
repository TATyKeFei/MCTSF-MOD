package icu.tatyrealms.tatykefei.body;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The worker owns only detached numerical data, never entities, levels or the live saved pose. */
final class AsyncBodyPhysics implements AutoCloseable {
    static final int MAX_PENDING = 64;
    private final ThreadPoolExecutor executor;

    record Result(BodyRigPose pose, BodyRigPhysics physics, double displacement) {
    }

    AsyncBodyPhysics() {
        int threads = Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 2);
        executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING),
                Thread.ofPlatform().daemon().name("TSF body physics-", 1).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    Future<Result> submit(BodyRigPose livePose, BodyRigPhysics livePhysics, Vec3 position,
            List<AABB> collisions, int part, Vec3 target, boolean simulate) {
        BodyRigPose pose = livePose.copy();
        BodyRigPhysics physics = livePhysics.copy();
        List<AABB> boxes = List.copyOf(collisions);
        try {
            return executor.submit(() -> {
                double[] before = pose.points.clone();
                physics.step(position, boxes, pose, part, target, simulate);
                double displacement = 0;
                for (int node = 0; node < BodyRigPose.COUNT; node++) {
                    int index = node * 3;
                    double x = pose.points[index] - before[index];
                    double y = pose.points[index + 1] - before[index + 1];
                    double z = pose.points[index + 2] - before[index + 2];
                    displacement = Math.max(displacement, x * x + y * y + z * z);
                }
                return new Result(pose, physics, displacement);
            });
        } catch (RejectedExecutionException exception) {
            return null;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
