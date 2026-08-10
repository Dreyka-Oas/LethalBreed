package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.pack.PackJoinRule.Decision;
import com.dreykaoas.lethalbreed.pack.PackJoinRule.Kind;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The whole membership decision, exercised without a world.
 *
 * <p>Everything the rule needs is a handful of primitives about the neighbourhood, which is the point: the
 * caller reduces its world question to arrays, and every branch — including the degenerate ones — becomes
 * scriptable here rather than only observable in a running server.
 */
class PackJoinRuleTest {

    private static final int NO_PACK = 0;

    private final boolean savedEnabled = PackConfig.packEnabled;
    private final int savedFormMin = PackConfig.packFormMinSize;
    private final int savedMaxSize = PackConfig.packMaxSize;
    private final int savedMinNeighbours = PackConfig.packMinNeighbours;
    private final int savedStray = PackConfig.packStrayActivations;
    private final double savedBreak = PackConfig.packBreakRadius;

    @AfterEach
    void restoreConfig() {
        PackConfig.packEnabled = savedEnabled;
        PackConfig.packFormMinSize = savedFormMin;
        PackConfig.packMaxSize = savedMaxSize;
        PackConfig.packMinNeighbours = savedMinNeighbours;
        PackConfig.packStrayActivations = savedStray;
        PackConfig.packBreakRadius = savedBreak;
    }

    /** Neighbour list builder: ids are the entity ids, packs the pack each neighbour belongs to (0 = none). */
    private static Decision decideAlone(int myEntityId, int[] ids, long[] packs) {
        double[] distSq = new double[ids.length];
        return PackJoinRule.decide(NO_PACK, myEntityId, 0, 0.0, 0, packs, ids, distSq, ids.length);
    }

    // ---- « s'il n'y en a pas autour, ça ne sert à rien » ----

    @Test
    void aLoneZombieFormsNothing() {
        assertEquals(new Decision(Kind.NONE, 0L), decideAlone(7, new int[0], new long[0]));
    }

    @Test
    void belowTheNeighbourFloorNothingHappens() {
        PackConfig.packMinNeighbours = 3;
        Decision d = decideAlone(7, new int[] {8, 9}, new long[] {0L, 0L});
        assertEquals(Kind.NONE, d.kind());
    }

    @Test
    void packInstinctDisabledNeverDecides() {
        PackConfig.packEnabled = false;
        Decision d = decideAlone(1, new int[] {2, 3, 4, 5}, new long[] {0L, 0L, 0L, 0L});
        assertEquals(Kind.NONE, d.kind());
    }

    // ---- Formation, et l'élection qui garantit une seule meute par grappe ----

    @Test
    void aClusterFormsExactlyOnePackElectedByLowestId() {
        PackConfig.packFormMinSize = 3;
        int[] ids = {4, 9, 12};
        long[] packs = {0L, 0L, 0L};
        // Le plus petit id de la grappe {2,4,9,12} est 2 : lui seul forme.
        assertEquals(Kind.FORM, decideAlone(2, ids, packs).kind());
        // Tous les autres se taisent — sinon la grappe produirait quatre meutes d'un seul membre.
        assertEquals(Kind.NONE, decideAlone(4, new int[] {2, 9, 12}, packs).kind());
        assertEquals(Kind.NONE, decideAlone(9, new int[] {2, 4, 12}, packs).kind());
        assertEquals(Kind.NONE, decideAlone(12, new int[] {2, 4, 9}, packs).kind());
    }

    @Test
    void aClusterTooSmallToFormStaysLoose() {
        PackConfig.packFormMinSize = 4;
        // Trois zombies au total (moi + deux voisins) alors qu'il en faut quatre.
        assertEquals(Kind.NONE, decideAlone(1, new int[] {2, 3}, new long[] {0L, 0L}).kind());
    }

    // ---- Adhésion ----

    @Test
    void joinsTheNeighbourPack() {
        Decision d = decideAlone(1, new int[] {2, 3}, new long[] {77L, 77L});
        assertEquals(new Decision(Kind.JOIN, 77L), d);
    }

    @Test
    void joinsTheMostRepresentedPack() {
        Decision d = decideAlone(1, new int[] {2, 3, 4, 5}, new long[] {5L, 9L, 9L, 0L});
        assertEquals(new Decision(Kind.JOIN, 9L), d);
    }

    @Test
    void aTieIsBrokenByTheLowestPackIdSoTheChoiceIsDeterministic() {
        Decision d = decideAlone(1, new int[] {2, 3}, new long[] {42L, 8L});
        assertEquals(new Decision(Kind.JOIN, 8L), d);
    }

    @Test
    void aFullPackIsNotJoined() {
        PackConfig.packMaxSize = 2;
        double[] distSq = new double[] {1.0, 1.0};
        // Les deux voisins appartiennent à la meute 77, qui est déjà pleine.
        Decision d = PackJoinRule.decide(NO_PACK, 1, 2, 0.0, 0,
                new long[] {77L, 77L}, new int[] {2, 3}, distSq, 2);
        assertEquals(Kind.NONE, d.kind());
    }

    @Test
    void anExistingPackWinsOverFormingANewOne() {
        PackConfig.packFormMinSize = 2;
        // Assez de monde pour former, mais un voisin a déjà une meute : on la rejoint.
        Decision d = decideAlone(1, new int[] {2, 3, 4}, new long[] {0L, 31L, 0L});
        assertEquals(new Decision(Kind.JOIN, 31L), d);
    }

    // ---- Départ, et son hystérésis ----

    private static Decision member(double distToCentroidSq, int strayCount) {
        return PackJoinRule.decide(5L, 1, 4, distToCentroidSq, strayCount,
                new long[0], new int[0], new double[0], 0);
    }

    @Test
    void aMemberInsideTheBreakRadiusStays() {
        PackConfig.packBreakRadius = 40.0;
        assertEquals(Kind.NONE, member(30.0 * 30.0, 0).kind());
    }

    @Test
    void strayingIsToleratedUntilTheThirdConsecutiveActivation() {
        PackConfig.packBreakRadius = 40.0;
        PackConfig.packStrayActivations = 3;
        double far = 50.0 * 50.0;
        // Un zombie qui contourne un mur sort du rayon deux activations sans quitter sa meute.
        assertEquals(Kind.NONE, member(far, 0).kind());
        assertEquals(Kind.NONE, member(far, 1).kind());
        assertEquals(Kind.LEAVE, member(far, 2).kind());
    }

    @Test
    void comingBackInsideResetsTheStrayCounter() {
        PackConfig.packBreakRadius = 40.0;
        PackConfig.packStrayActivations = 3;
        // Déjà deux activations dehors, mais revenu dans le rayon : la décision ne part pas.
        assertEquals(Kind.NONE, member(10.0 * 10.0, 2).kind());
    }

    @Test
    void strayResetIsSignalledSoTheCallerCanClearItsCounter() {
        PackConfig.packBreakRadius = 40.0;
        assertEquals(0, PackJoinRule.nextStrayCount(10.0 * 10.0, 2));
        assertEquals(3, PackJoinRule.nextStrayCount(50.0 * 50.0, 2));
    }

    @Test
    void aMemberNeverTriesToJoinAnotherPack() {
        // Même entouré d'une autre meute, un membre en place ne change pas de camp : seul le départ, puis
        // une adhésion à l'activation suivante, peuvent le déplacer. Sinon deux meutes voisines se
        // videraient l'une dans l'autre à chaque passe.
        Decision d = PackJoinRule.decide(5L, 1, 4, 0.0, 0,
                new long[] {99L, 99L}, new int[] {2, 3}, new double[] {1.0, 1.0}, 2);
        assertEquals(Kind.NONE, d.kind());
    }

    // ---- Rejoin radius ----

    @Test
    void aMemberWellInsideTheRejoinRadiusIsNotTooFar() {
        assertEquals(false, PackJoinRule.outsideRejoinRadius(30.0 * 30.0, 64.0));
    }

    @Test
    void aMemberBeyondTheRejoinRadiusIsTooFar() {
        assertEquals(true, PackJoinRule.outsideRejoinRadius(100.0 * 100.0, 64.0));
    }

    @Test
    void exactlyAtTheRejoinRadiusIsStillCloseEnough() {
        assertEquals(false, PackJoinRule.outsideRejoinRadius(64.0 * 64.0, 64.0));
    }

    @Test
    void aNegativeConfiguredRadiusBehavesAsZero() {
        // Bounds already keep packRejoinRadius positive in practice, but the guard must not let a
        // misconfigured negative radius flip the comparison and accept anything as "close enough".
        assertEquals(true, PackJoinRule.outsideRejoinRadius(1.0, -10.0));
        assertEquals(false, PackJoinRule.outsideRejoinRadius(0.0, -10.0));
    }
}
