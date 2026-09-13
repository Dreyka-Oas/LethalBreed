/**
 * Coarse spatial structures the AI queries every tick: the zombie grid, the prey index, and the cell
 * arithmetic they share.
 *
 * <p><b>Why this exists.</b> The direct way to acquire targets is to ask the world:
 * {@code getEntitiesOfClass(LivingEntity.class, box, isValid)} over an 80-block box. That visits every
 * entity in the box and runs the predicate on each, and the predicate rejects {@code Zombie}, which in
 * this mod is nearly everything in the box. So each zombie paid for walking the whole horde in order to
 * discard it: O(zombies²) per bucket cycle. Measured with {@code StageProfiler} at ~22us per activation,
 * 50% of the reclassify stage and ~20% of the mod's entire tick budget, at only ~100 zombies.
 *
 * <p>Indexing the prey instead makes a query cost O(cells probed + prey nearby), independent of how large
 * the horde grows. Zombies are never inserted, so they are never visited.
 *
 * <p><b>Players are deliberately NOT indexed.</b> They are queried live from the level at lookup time.
 * A player is the highest-stakes target in the game, and there is no acceptable failure mode where a
 * bookkeeping slip makes one invisible to the horde; there are also never more than a handful, so
 * scanning them directly costs nothing. This index only holds the cheap-to-lose, numerous prey.
 *
 * <p><b>Lifetime.</b> Entries hold live {@code LivingEntity} references, which pin the entity, its level
 * and the server. They are dropped on {@code ENTITY_UNLOAD}, again defensively in {@link #refresh()} for
 * anything that died or was removed without the event reaching us, and wholesale when the per-dimension
 * context is discarded at {@code SERVER_STOPPED}. That belt-and-braces shape is deliberate: the same
 * structure without it is exactly the leak that audit finding P7-1 was.
 */
package oas.dreyka.lethalbreed.spatial;
