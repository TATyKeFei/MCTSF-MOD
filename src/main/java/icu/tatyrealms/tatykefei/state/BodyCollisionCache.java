package icu.tatyrealms.tatykefei.state;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Main-thread-only cache of small collision cells shared by nearby bodies. */
final class BodyCollisionCache {
    private static final int CELL_SIZE = 4;
    private static final int MAX_CELLS = 2048;
    private final Map<ServerLevel, Cells> levels = new HashMap<>();

    private static final class Cells {
        final LinkedHashMap<BlockPos, Cell> entries = new LinkedHashMap<>(64, 0.75F, true);
        long revision;
    }

    private record Box(BlockPos block, AABB bounds) {
    }

    private record Cell(BlockPos origin, WeakReference<LevelChunk> chunk, List<Box> boxes, long expires) {
    }

    /** Collision shapes may inspect neighbours; these reads must never load a chunk. */
    private record LoadedBlocks(ServerLevel level) implements BlockGetter {
        private LevelChunk chunk(BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? null : chunk.getBlockEntity(pos);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }
    }

    static final class Snapshot {
        final ServerLevel level;
        final AABB coverage;
        final List<AABB> boxes;
        private final List<Cell> cells;
        private long revision;
        private long checked;
        private final long expires;

        Snapshot(ServerLevel level, AABB coverage, List<AABB> boxes, List<Cell> cells, long revision, long time) {
            this.level = level;
            this.coverage = coverage;
            this.boxes = List.copyOf(boxes);
            this.cells = List.copyOf(cells);
            this.revision = revision;
            checked = time;
            expires = cells.stream().mapToLong(Cell::expires).min().orElse(Long.MAX_VALUE);
        }

        boolean covers(Vec3 pelvis) {
            return coverage.deflate(1.65).contains(pelvis);
        }
    }

    private static BlockPos cellAt(BlockPos pos) {
        return new BlockPos(Math.floorDiv(pos.getX(), CELL_SIZE) * CELL_SIZE,
                Math.floorDiv(pos.getY(), CELL_SIZE) * CELL_SIZE,
                Math.floorDiv(pos.getZ(), CELL_SIZE) * CELL_SIZE);
    }

    void changed(ServerLevel level, BlockPos pos) {
        Cells cells = levels.get(level);
        if (cells == null || cells.entries.isEmpty()) {
            return;
        }
        // Shapes can extend outside their block or depend on an adjacent block.
        BlockPos min = cellAt(pos.offset(-1, -1, -1));
        BlockPos max = cellAt(pos.offset(1, 1, 1));
        boolean removed = false;
        for (int x = min.getX(); x <= max.getX(); x += CELL_SIZE) {
            for (int y = min.getY(); y <= max.getY(); y += CELL_SIZE) {
                for (int z = min.getZ(); z <= max.getZ(); z += CELL_SIZE) {
                    removed |= cells.entries.remove(new BlockPos(x, y, z)) != null;
                }
            }
        }
        if (removed) {
            cells.revision++;
        }
    }

    boolean valid(Snapshot snapshot, long time) {
        Cells cells = levels.get(snapshot.level);
        if (cells == null || time >= snapshot.expires) {
            return false;
        }
        if (snapshot.revision == cells.revision && time - snapshot.checked < 20) {
            return true;
        }
        for (Cell cell : snapshot.cells) {
            LevelChunk chunk = cell.chunk.get();
            if (chunk == null || cells.entries.get(cell.origin) != cell || snapshot.level.getChunkSource()
                    .getChunkNow(cell.origin.getX() >> 4, cell.origin.getZ() >> 4) != chunk) {
                return false;
            }
        }
        snapshot.revision = cells.revision;
        snapshot.checked = time;
        return true;
    }

    Snapshot capture(ServerLevel level, Vec3 pelvis, BlockPos ignored, long time) {
        Cells cells = levels.computeIfAbsent(level, unused -> new Cells());
        // Joint limits bound every body part to within 1.5 blocks of the pinned pelvis.
        AABB coverage = new AABB(pelvis, pelvis).inflate(1.9);
        BlockPos min = cellAt(BlockPos.containing(coverage.minX - 1, coverage.minY - 1, coverage.minZ - 1));
        BlockPos max = cellAt(BlockPos.containing(coverage.maxX + 1, coverage.maxY + 1, coverage.maxZ + 1));
        List<Cell> used = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x += CELL_SIZE) {
            for (int y = min.getY(); y <= max.getY(); y += CELL_SIZE) {
                for (int z = min.getZ(); z <= max.getZ(); z += CELL_SIZE) {
                    BlockPos origin = new BlockPos(x, y, z);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) {
                        return null; // Never load or generate a chunk for a physics snapshot.
                    }
                    Cell cell = cells.entries.get(origin);
                    if (cell == null || cell.chunk.get() != chunk || time >= cell.expires) {
                        cell = readCell(level, chunk, origin, time);
                        cells.entries.put(origin, cell);
                        cells.revision++;
                        if (cells.entries.size() > MAX_CELLS) {
                            cells.entries.pollFirstEntry();
                        }
                    }
                    used.add(cell);
                    for (Box box : cell.boxes) {
                        if (!box.block.equals(ignored) && box.bounds.intersects(coverage)) {
                            boxes.add(box.bounds);
                        }
                    }
                }
            }
        }
        return new Snapshot(level, coverage, boxes, used, cells.revision, time);
    }

    private static Cell readCell(ServerLevel level, LevelChunk chunk, BlockPos origin, long time) {
        List<Box> boxes = new ArrayList<>();
        BlockGetter loaded = new LoadedBlocks(level);
        boolean dynamic = false;
        for (BlockPos cursor : BlockPos.betweenClosed(origin, origin.offset(CELL_SIZE - 1, CELL_SIZE - 1, CELL_SIZE - 1))) {
            if (level.isOutsideBuildHeight(cursor)) {
                continue;
            }
            var state = chunk.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            dynamic |= state.getBlock().hasDynamicShape();
            BlockPos pos = cursor.immutable();
            for (AABB box : state.getCollisionShape(loaded, pos).toAabbs()) {
                boxes.add(new Box(pos, box.move(pos)));
            }
        }
        return new Cell(origin, new WeakReference<>(chunk), List.copyOf(boxes), dynamic ? time + 2 : Long.MAX_VALUE);
    }

    void clear() {
        levels.clear();
    }
}
