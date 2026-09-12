package oas.dreyka.lethalbreed.block;

import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a block may be broken by a zombie.
 *
 * <p>The hardness rule below is a good default and a poor law: it knows nothing about a modded block that
 * is soft but must never be dug, or a hard one an addon wants breached anyway. The two tags are the way
 * out, and like {@code lethalbreed:spawn_protected} they cost a JSON file rather than a listener. Both are
 * empty in the shipped jar.
 */
public final class MaterialRegistry {
    private MaterialRegistry() {}

    /** Blocks a zombie must never break, whatever their hardness says. Checked before everything else, so
     *  this is the one answer nothing else can overturn. */
    public static final TagKey<Block> UNBREAKABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("lethalbreed", "zombie_unbreakable"));

    /** Blocks a zombie may break even though the hardness ceiling would refuse them. Does not override
     *  {@link #UNBREAKABLE}, the block-entity guard or an unbreakable block's negative hardness: this
     *  raises the ceiling for a block, it does not turn off anti-grief or let anything mine bedrock. */
    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("lethalbreed", "zombie_breakable"));

    public static boolean isBreakable(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(UNBREAKABLE)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false; // never "break" liquids
        }
        if (CombatMoveConfig.breakProtectBlockEntities && state.hasBlockEntity()) {
            return false; // anti-grief: spare chests, furnaces, spawners, beds, etc.
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0f) {
            return false; // bedrock, barrier, etc.
        }
        return state.is(BREAKABLE) || hardness <= CombatMoveConfig.breakMaxHardness;
    }
}
