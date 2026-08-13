package com.dreykaoas.lethalbreed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brigadier behaviour the split of {@code /lethaldev} rests on.
 *
 * <p>{@code PlagueCommand} (src/main, shipped) registers the {@code lethaldev} literal carrying
 * {@code level} and {@code cure}; {@code LethalDevCommand} (src/dev, never packaged) registers the same
 * literal carrying the other four subcommands. That only produces one working command tree because {@code CommandNode.addChild}
 * merges into an existing child of the same name instead of replacing it or adding a duplicate.
 *
 * <p>Nothing here touches Minecraft — Brigadier is a plain library, so the contract can be pinned headlessly.
 * If a Brigadier update ever changed merging to last-registration-wins, the dev environment would silently
 * lose either {@code level} or everything else, and this is the test that would say so.
 */
class LiteralMergeTest {

    private static LiteralArgumentBuilder<Object> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    @Test
    void tworegistrationsOfOneLiteralMergeIntoASingleNode() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        // Shipped half: the literal plus its one subcommand.
        dispatcher.register(literal("lethaldev").then(literal("level").executes(c -> 1)));
        // Dev half: the same literal, different subcommands.
        dispatcher.register(literal("lethaldev")
                .then(literal("cure").executes(c -> 1))
                .then(literal("status").executes(c -> 1)));

        assertEquals(1, dispatcher.getRoot().getChildren().size(),
                "a second registration must not create a rival root node");
        CommandNode<Object> root = dispatcher.getRoot().getChild("lethaldev");
        assertNotNull(root);
        assertNotNull(root.getChild("level"), "the shipped subcommand must survive the dev registration");
        assertNotNull(root.getChild("cure"), "the dev subcommands must be added to the same node");
        assertNotNull(root.getChild("status"));
        assertEquals(3, root.getChildren().size());
    }

    @Test
    void theSurvivingRequirementIsTheFirstRegistrationsOne() {
        // Why both halves must gate at the same permission level: a merge keeps the node that was there
        // first, so the dev registration's requires() is discarded. Were the shipped half ever gated more
        // loosely than the dev half, the dev subcommands would inherit the looser gate.
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("lethaldev").requires(s -> false).then(literal("level").executes(c -> 1)));
        dispatcher.register(literal("lethaldev").requires(s -> true).then(literal("cure").executes(c -> 1)));

        assertTrue(dispatcher.getRoot().getChild("lethaldev").getChildren().size() == 2,
                "both branches are present regardless of the requirement");
        assertEquals(0, dispatcher.getSmartUsage(dispatcher.getRoot(), new Object()).size(),
                "the FIRST registration's requires() is the one that survives the merge");
    }

    @Test
    void aGateOnTheBranchSurvivesWhereAGateOnTheLiteralWouldNot() {
        // How /lethalphase is built, and why. The shipped half registers the literal UNGATED so any player
        // can read the phase; the dev half adds the forcing branch. Gating that branch's own node is the
        // only placement that works — a requires() on the dev literal is dropped by the merge, which would
        // hand phase forcing to every player in the dev environment.
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("lethalphase").executes(c -> 1));                      // shipped, ungated
        dispatcher.register(literal("lethalphase")
                .then(literal("force").requires(s -> false).executes(c -> 1)));            // dev, gated node

        CommandNode<Object> root = dispatcher.getRoot().getChild("lethalphase");
        assertTrue(root.canUse(new Object()), "the readout stays open to everyone");
        assertNotNull(root.getChild("force"));
        assertTrue(!root.getChild("force").canUse(new Object()),
                "a requires() on the child node is NOT discarded by the merge");
    }
}
