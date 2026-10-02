package com.groid.papon.command;

import com.groid.papon.entity.PaponState;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.journey.JourneyData;
import com.groid.papon.journey.TacoTruck;
import com.groid.papon.journey.Terrain;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /papon - admin tools for the Taco Quest (permission level 2).
 *   locate              every journey in this dimension, loaded or not
 *   skip <blocks>       nearest loaded Papon jumps ahead along his route (testing)
 *   arrive              nearest loaded Papon arrives where he stands (testing)
 *   remove              nearest loaded Papon within 16 blocks is deleted (the only way he goes)
 *   escort <on|off>     treat every Papon as escorted, for testing with nobody around
 *   placetruck          place the Taco Truck in front of you (testing)
 *   state <STATE>       force the nearest Papon into a state, to see an animation (testing)
 */
public final class PaponCommand {
    private PaponCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("papon").requires(s -> s.hasPermission(2))
                .then(Commands.literal("locate").executes(PaponCommand::locate))
                .then(Commands.literal("skip").then(Commands.argument("blocks", IntegerArgumentType.integer(1, 100_000))
                        .executes(c -> skip(c, IntegerArgumentType.getInteger(c, "blocks")))))
                .then(Commands.literal("arrive").executes(PaponCommand::arrive))
                .then(Commands.literal("remove").executes(PaponCommand::remove))
                .then(Commands.literal("escort").then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> escort(c, BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("placetruck").executes(PaponCommand::placeTruck))
                .then(Commands.literal("state").then(Commands.argument("state", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(PaponState.values()).map(Enum::name), b))
                        .executes(c -> state(c, StringArgumentType.getString(c, "state"))))));
    }

    private static int locate(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Map<UUID, JourneyData.Journey> journeys = JourneyData.get(level).journeys();
        if (journeys.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No Wandering Papons in this dimension."), false);
            return 0;
        }
        for (JourneyData.Journey j : journeys.values()) {
            BlockPos p = j.lastPos;
            BlockPos d = j.destination;
            int left = (int) Math.sqrt(Math.pow(p.getX() - d.getX(), 2) + Math.pow(p.getZ() - d.getZ(), 2));
            String line = String.format("%s  %s at %d %d %d  ->  truck at %d %d (%d blocks left)",
                    j.id.toString().substring(0, 8), j.state, p.getX(), p.getY(), p.getZ(), d.getX(), d.getZ(), left);
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return journeys.size();
    }

    private static int skip(CommandContext<CommandSourceStack> c, int blocks) {
        WanderingPaponEntity p = nearest(c, 64);
        if (p == null || p.getDestination() == null) return fail(c, "No Wandering Papon within 64 blocks.");
        BlockPos d = p.getDestination();
        double a = Math.atan2(d.getZ() - p.getZ(), d.getX() - p.getX());
        double step = Math.min(blocks, Math.max(0, p.remainingDistance() - 6));
        int x = Mth.floor(p.getX() + Math.cos(a) * step);
        int z = Mth.floor(p.getZ() + Math.sin(a) * step);
        ServerLevel level = c.getSource().getLevel();
        level.getChunk(x >> 4, z >> 4);
        BlockPos s = Terrain.findDrySpotNear(level, x, z, 16, -1);
        if (s == null) return fail(c, "No dry ground near " + x + " " + z + ".");
        p.hopTo(s);
        // Keep the new spot loaded for a moment so he lands somewhere that ticks.
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(s), 1, p.getId());
        c.getSource().sendSuccess(() -> Component.literal("Papon skipped to " + s.toShortString() + ", "
                + (int) p.remainingDistance() + " blocks left."), true);
        return 1;
    }

    private static int arrive(CommandContext<CommandSourceStack> c) {
        WanderingPaponEntity p = nearest(c, 64);
        if (p == null) return fail(c, "No Wandering Papon within 64 blocks.");
        if (p.getPaponState() != PaponState.WALKING) p.setPaponState(PaponState.WALKING);
        p.arriveNearby();
        c.getSource().sendSuccess(() -> Component.literal("Papon has arrived."), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) {
        WanderingPaponEntity p = nearest(c, 16);
        if (p == null) return fail(c, "No Wandering Papon within 16 blocks.");
        p.discard();
        c.getSource().sendSuccess(() -> Component.literal("Wandering Papon removed."), true);
        return 1;
    }

    private static int escort(CommandContext<CommandSourceStack> c, boolean on) {
        WanderingPaponEntity.debugEscort = on;
        c.getSource().sendSuccess(() -> Component.literal("Debug escort " + (on ? "ON: Papons walk with nobody near."
                : "OFF.")), true);
        return 1;
    }

    private static int placeTruck(CommandContext<CommandSourceStack> c) {
        Vec3 pos = c.getSource().getPosition();
        Direction facing = Direction.fromYRot(c.getSource().getRotation().y);
        boolean ok = TacoTruck.place(c.getSource().getLevel(), BlockPos.containing(pos), facing, 2).isPresent();
        return ok ? 1 : fail(c, "Taco Truck template missing.");
    }

    private static int state(CommandContext<CommandSourceStack> c, String name) {
        WanderingPaponEntity p = nearest(c, 64);
        if (p == null) return fail(c, "No Wandering Papon within 64 blocks.");
        PaponState s;
        try {
            s = PaponState.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fail(c, "Unknown state " + name + ".");
        }
        p.setPaponState(s);
        c.getSource().sendSuccess(() -> Component.literal("Papon is now " + s + "."), true);
        return 1;
    }

    @Nullable
    private static WanderingPaponEntity nearest(CommandContext<CommandSourceStack> c, double radius) {
        Vec3 pos = c.getSource().getPosition();
        List<WanderingPaponEntity> found = c.getSource().getLevel().getEntitiesOfClass(WanderingPaponEntity.class,
                new AABB(pos, pos).inflate(radius));
        return found.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(pos))).orElse(null);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
