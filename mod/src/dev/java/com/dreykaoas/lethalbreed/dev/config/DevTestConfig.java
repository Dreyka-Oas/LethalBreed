package com.dreykaoas.lethalbreed.dev.config;

/**
 * Development-only toggles: the headless verification arenas, the perf-recap interval and the
 * {@code /lethalspawn} radius.
 *
 * <p>This holder lives in the {@code dev} source set and is never packaged, so a player's jar has no such
 * options at all: they are absent from {@code lethalbreed.json}, absent from {@code /lethalconfig}, and the
 * GUI's "Dev / Debug" sidebar tab does not exist because no row ever carries that category. In a development
 * environment {@code DevBootstrap} calls {@code ConfigSchema.registerHolder(DevTestConfig.class)} — before
 * the JSON is read, so a developer's own values still load — and {@code ConfigBounds.registerGroup} adds the
 * matching ranges from {@link DevBounds}.
 *
 * <p>Because registration happens at runtime, this holder has no fixed position in
 * {@code ConfigSchema.HOLDERS}: it is appended last, so its options are written after every shipped one.
 * That is invisible to players and irrelevant to {@code ConfigSchemaOrderTest}, which pins the SHIPPED
 * option order only.
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
    /** Attribute-ceiling suite: spawns zombies at extreme phases and checks nothing can one-shot a player. */
    public static boolean devCapsTest = false;
    /** Dev: halt the server as soon as a harness prints its {@code [LB-Verify] ALL DONE} verdict, so a scripted
     *  {@code runServer} exits on its own instead of hanging until the driver's timeout. Off for shipping. */
    public static boolean devAutoHalt = false;

    // ---- Dev climb test (headless) ----
    /** Build a wall + villager-on-top + zombies arena on server start, for autonomous climb testing. */
    public static boolean devClimbTest = false;

    /** Radius (blocks) around the player used by the /lethalspawn dev command. */
    public static int devSpawnRadius = 16;

    /** How often (ticks) to emit the dev perf recap (100 ticks = 5s). 0 disables (default — no log spam).
     *  <p>Moved here from {@code SchedulerConfig} with the rest of the dev options: its only readers
     *  ({@code StageProfiler#enabled}, {@code PerfRecap#maybeLog}, {@code /lethalspawn}) are all dev-side, so
     *  in a player jar it was an option that could be set and could never do anything. */
    public static int debugLogInterval = 0;

    // ---- Trace channels (DevProbe) ----
    /** Emit a {@code [ClimbDbg]} line (1-in-4) for every pursuing zombie's climb decision. Off for shipping;
     *  {@code ClimbTest} turns its own channel on for the duration of its run via {@code DevProbe.setTracing}. */
    public static boolean debugClimb = false;
    /** Emit a {@code [PackDbg]} line for every pack decision. Off for shipping; {@code PackSetup} turns the
     *  channel on for one stage only via {@code DevProbe.setTracing} — at divisor 1 this is a line per zombie
     *  per tick, so more than one stage at once floods the log. */
    public static boolean debugPacks = false;
    /** Show every tracked contamination victim's stage as a live action-bar tag (players) or name tag (mobs).
     *  Off for shipping. */
    public static boolean debugContam = false;
}
