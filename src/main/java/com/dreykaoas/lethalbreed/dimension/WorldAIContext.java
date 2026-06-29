package com.dreykaoas.lethalbreed.dimension;

import com.dreykaoas.lethalbreed.ai.flowfield.FlowFieldManager;
import com.dreykaoas.lethalbreed.block.BlockOperationQueue;
import com.dreykaoas.lethalbreed.block.BreakManager;
import com.dreykaoas.lethalbreed.block.PlacedBlockTracker;
import com.dreykaoas.lethalbreed.sound.SoundEventBus;
import com.dreykaoas.lethalbreed.spatial.SpatialGrid;

/**
 * Per-dimension AI state container: a spatial grid for neighbour/sound queries and a flow-field
 * manager for navigation. One independent instance per dimension.
 */
public final class WorldAIContext {
    private final SpatialGrid spatialGrid;
    private final FlowFieldManager flowFieldManager;
    private final BlockOperationQueue blockOps;
    private final PlacedBlockTracker placedBlocks;
    private final BreakManager breakManager;
    private final SoundEventBus soundBus;

    public WorldAIContext() {
        this.spatialGrid = new SpatialGrid();
        this.flowFieldManager = new FlowFieldManager();
        this.blockOps = new BlockOperationQueue();
        this.placedBlocks = new PlacedBlockTracker();
        this.breakManager = new BreakManager();
        this.soundBus = new SoundEventBus();
    }

    public BreakManager breakManager() {
        return breakManager;
    }

    public SoundEventBus soundBus() {
        return soundBus;
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
