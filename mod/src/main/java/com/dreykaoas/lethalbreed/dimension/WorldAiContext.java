package com.dreykaoas.lethalbreed.dimension;

import com.dreykaoas.lethalbreed.ai.flowfield.FlowFieldManager;
import com.dreykaoas.lethalbreed.block.BlockOperationQueue;
import com.dreykaoas.lethalbreed.block.BreachCoordinator;
import com.dreykaoas.lethalbreed.block.BreakManager;
import com.dreykaoas.lethalbreed.block.PlacedBlockTracker;
import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.sound.SoundEventBus;
import com.dreykaoas.lethalbreed.spatial.SpatialGrid;
import com.dreykaoas.lethalbreed.spatial.TargetIndex;

/**
 * Per-dimension AI state container: a spatial grid for neighbour/sound queries and a flow-field
 * manager for navigation. One independent instance per dimension.
 */
public final class WorldAiContext {
    private final SpatialGrid spatialGrid;
    private final FlowFieldManager flowFieldManager;
    private final BlockOperationQueue blockOps;
    private final PlacedBlockTracker placedBlocks;
    private final BreakManager breakManager;
    private final BreachCoordinator breachCoordinator;
    private final SoundEventBus soundBus;
    private final TargetIndex targetIndex;
    private final PackManager packManager;

    public WorldAiContext() {
        this.spatialGrid = new SpatialGrid();
        this.flowFieldManager = new FlowFieldManager();
        this.blockOps = new BlockOperationQueue();
        this.placedBlocks = new PlacedBlockTracker();
        this.breakManager = new BreakManager();
        this.breachCoordinator = new BreachCoordinator();
        this.soundBus = new SoundEventBus();
        this.targetIndex = new TargetIndex();
        this.packManager = new PackManager();
    }

    /** Pack instinct and migration for this dimension — a pack never crosses into another. */
    public PackManager packManager() {
        return packManager;
    }

    public BreakManager breakManager() {
        return breakManager;
    }

    public BreachCoordinator breachCoordinator() {
        return breachCoordinator;
    }

    public SoundEventBus soundBus() {
        return soundBus;
    }

    /** Prey index used by target acquisition — see {@link TargetIndex} for why it exists. */
    public TargetIndex targetIndex() {
        return targetIndex;
    }

    public SpatialGrid spatialGrid() {
        return spatialGrid;
    }

    public FlowFieldManager flowFieldManager() {
        return flowFieldManager;
    }

    public BlockOperationQueue blockOps() {
        return blockOps;
    }

    public PlacedBlockTracker placedBlocks() {
        return placedBlocks;
    }
}
