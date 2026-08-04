package com.dreykaoas.lethalbreed.config.domain.engine;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

/**
 * Development-only toggles: the headless verification arenas, the climb debug stream and the
 * {@code /lethalspawn} radius. Every one of these is off for shipping; the {@code dev} source set that
 * reads them is never packaged into a player jar.
 *
 * <p>Split out of {@code ProgressionConfig}, which held three unrelated domains. Field NAMES are
 * unchanged and the holders stay adjacent in {@code ConfigSchema.HOLDERS} in their original order, so
 * the on-disk JSON, {@code ConfigBoundsTable}, {@code ConfigCategory} and every translation key are
 * unaffected — see {@code ConfigSchemaOrderTest}.
 */
public final class DevTestConfig {
    private DevTestConfig() {}

    /** Dev: headless special-zombie verification arena on server start (logs PASS/FAIL). Off for shipping. */
    public static boolean devSpecialTest = false;
    /** Dev: headless mechanics arena (sun-burn / phase gear / contamination). Off for shipping. */
    public static boolean devMechTest = false;
    /** Dev: headless Compute-backend self-test on server start — solves a synthetic field on CPU and GPU and
     *  logs CPU sanity + GPU/CPU parity + dynamic-pool + routing checks. No world mutation. Off for shipping. */
    public static boolean devComputeTest = false;
    /** Dev: headless Super Contamination arena (infect → symptom → ramping DoT → death). Off for shipping. */
    public static boolean devPlagueTest = false;
    /** Dev: headless statue/freeze arena (a frozen zombie holds its pose and thaws on wake). Off for shipping. */
    public static boolean devStatueTest = false;
    /** Dev: headless cure/clear arena (the contamination clear-guard drops the infection). Off for shipping. */
    public static boolean devClearTest = false;
    /** Dev: headless placed-block arena (zombie-placed blocks are tracked, decayed and never farmed). Off for shipping. */
    public static boolean devPlacedTest = false;
    /** Dev: headless sun-shelter arena (a burning zombie detours to shade, or burns when disabled). Off for shipping. */
    public static boolean devShadeTest = false;
    /** Dev: headless breach arena (the breach coordinator picks and opens one shared wall route). Off for shipping. */
    public static boolean devBreachTest = false;
    /** Dev: headless synthetic-player proof — spawns a fake player + zombies and asserts the player lands in
     *  {@code level.players()}, a flow field gets built, and the zombies then acquire and close on it. This is
     *  the precondition every other arena harness depends on. Off for shipping. */
    public static boolean devPresenceTest = false;
    /** Pack instinct and migration arena. Off for shipping, like every other dev flag here. */
    public static boolean devPackTest = false;
    /** Dev: halt the server as soon as a harness prints its {@code [LB-Verify] ALL DONE} verdict, so a scripted
     *  {@code runServer} exits on its own instead of hanging until the driver's timeout. Off for shipping. */
    public static boolean devAutoHalt = false;

    // ---- Dev climb test (headless) ----
    /** Build a wall + villager-on-top + zombies arena on server start, for autonomous climb testing. */
    public static boolean devClimbTest = false;
    /** Log each targeting zombie's approach/climb state ([ClimbDbg] lines). Auto-enabled by the climb test. */
    public static boolean debugClimb = false;

    /** Radius (blocks) around the player used by the /lethalspawn dev command. */
    public static int devSpawnRadius = 16;
}
