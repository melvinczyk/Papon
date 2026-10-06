package com.groid.papon.command;

import com.groid.papon.PaponConfig;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.TacoBoss;
import com.groid.papon.vehicle.CaravanEntity;
import com.groid.papon.entity.PaponState;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
import com.groid.papon.journey.JourneyData;
import com.groid.papon.journey.JourneyPlanner;
import com.groid.papon.journey.TacoTruck;
import com.groid.papon.journey.TacoTruckScene;
import com.groid.papon.registry.ModEntities;
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
import java.util.Optional;
import java.util.UUID;

/**
 * /papon - admin tools for the Taco Quest (permission level 2).
 *   locate              every journey in this dimension, loaded or not
 *   skip <blocks>       nearest loaded Papon jumps ahead along his route (testing)
 *   arrive              nearest loaded Papon arrives where he stands (testing)
 *   remove              nearest loaded Papon within 16 blocks is deleted (the only way he goes)
 *   escort <on|off>     treat every Papon as escorted, for testing with nobody around
 *   placetruck          place the Taco Truck in front of you (testing)
 *   tacoline            the whole arrival in front of you: truck, vendor, line, and the nearest
 *                       Wandering Papon (or a new one) queued at the back, you behind him
 *   serve               the nearest truck finishes its current customer right now
 *   fraud               everyone ahead of the Wandering Papon is served at once: straight to the coupon
 *   distance [min] [max]  show / set how far new Taco Truck sites are (saved to config)
 *   target <x> <z>      send the nearest Papon somewhere else (testing)
 *   state <STATE>       force the nearest Papon into a state, to see an animation (testing)
 *   reveal              the nearest truck goes straight to the fraud: unmasking, then the boss fight
 *   boss                Chef Papon (the boss) right here, his van parked beside you (testing)
 *   van                 a van in front of you (testing; normally it comes with the truck)
 *   bossstate <STATE>   force the nearest boss into a state (testing)
 *   cutaway <WHO>       cut to the band now (TRUMPETS, VIOLINS, GUITARRON, STRINGS, CORO, STANDS; testing)
 *   giant <MOVE>        El Gigante now: summon, lunge, grab, swipe, slam, slam2, laser (testing)
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
                .then(Commands.literal("tacoline").executes(PaponCommand::tacoLine))
                .then(Commands.literal("serve").executes(PaponCommand::serve))
                .then(Commands.literal("fraud").executes(PaponCommand::fraud))
                .then(Commands.literal("distance").executes(PaponCommand::showDistance)
                        .then(Commands.argument("min", IntegerArgumentType.integer(1, 100_000))
                                .executes(c -> setDistance(c, IntegerArgumentType.getInteger(c, "min"),
                                        IntegerArgumentType.getInteger(c, "min")))
                                .then(Commands.argument("max", IntegerArgumentType.integer(1, 100_000))
                                        .executes(c -> setDistance(c, IntegerArgumentType.getInteger(c, "min"),
                                                IntegerArgumentType.getInteger(c, "max"))))))
                .then(Commands.literal("target").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> target(c, IntegerArgumentType.getInteger(c, "x"),
                                        IntegerArgumentType.getInteger(c, "z"))))))
                .then(Commands.literal("state").then(Commands.argument("state", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(PaponState.values()).map(Enum::name), b))
                        .executes(c -> state(c, StringArgumentType.getString(c, "state")))))
                .then(Commands.literal("reveal").executes(PaponCommand::reveal))
                .then(Commands.literal("boss").executes(PaponCommand::boss))
                .then(Commands.literal("van").executes(PaponCommand::van))
                .then(Commands.literal("serpents").executes(PaponCommand::serpents))
                .then(Commands.literal("giant").then(Commands.argument("move", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"summon", "lunge", "grab", "swipe", "slam", "slam2", "laser"}, b))
                        .executes(c -> giant(c, StringArgumentType.getString(c, "move")))))
                .then(Commands.literal("bossstate").then(Commands.argument("state", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(BossState.values()).map(Enum::name), b))
                        .executes(c -> bossState(c, StringArgumentType.getString(c, "state")))))
                .then(Commands.literal("cutaway").then(Commands.argument("who", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(PaponBossEntity.Cutaway.values()).map(Enum::name), b))
                        .executes(c -> cutaway(c, StringArgumentType.getString(c, "who"))))));
    }

    /** Quetzalcoatl and Kukulcan out of their smoke, to circle here (a look at them without the fight). */
    private static int serpents(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        net.minecraft.world.phys.Vec3 at = c.getSource().getPosition();
        for (int k = 0; k < 2; k++) {
            double a = Math.toRadians(-90 + (k == 0 ? -35 : 35));
            net.minecraft.world.phys.Vec3 portal = at.add(Math.cos(a) * 200, 95, Math.sin(a) * 200);
            var s = com.groid.papon.boss.SkySerpentEntity.create(level, k, at, portal, 90 + k * 14, 48 + k * 10, k == 0 ? 1 : -1);
            level.addFreshEntity(s);
        }
        return 1;
    }

    /** El Gigante's act now, at whoever ran it (the nearest boss's giant, summoned first if need be). */
    private static int giant(CommandContext<CommandSourceStack> c, String move) {
        ServerLevel level = c.getSource().getLevel();
        net.minecraft.world.phys.Vec3 at = c.getSource().getPosition();
        var bosses = level.getEntitiesOfClass(PaponBossEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(160));
        if (bosses.isEmpty()) return fail(c, "No boss nearby.");
        if (!(c.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity e)) return fail(c, "Run it as a player.");
        return bosses.get(0).devGiant(move, e) ? 1 : fail(c, "No giant move " + move + " (or no coliseum).");
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
        BlockPos window = BlockPos.containing(pos).relative(facing, 3);
        boolean ok = TacoTruck.place(c.getSource().getLevel(), window, facing.getOpposite(), 6).isPresent();
        return ok ? 1 : fail(c, "Taco Truck template missing.");
    }

    private static int tacoLine(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Vec3 pos = c.getSource().getPosition();
        Direction facing = Direction.fromYRot(c.getSource().getRotation().y);
        BlockPos spot = BlockPos.containing(pos);
        WanderingPaponEntity papon = nearest(c, 32);
        if (papon == null) {
            papon = ModEntities.WANDERING_PAPON.get().create(level);
            if (papon == null) return fail(c, "Could not create a Wandering Papon.");
            Vec3 at = Vec3.atBottomCenterOf(spot).add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5);
            papon.moveTo(at.x, at.y, at.z, facing.toYRot(), 0);
            level.addFreshEntity(papon);
        }
        papon.skipToTruck();
        Optional<TacoVendorEntity> vendor = TacoTruckScene.open(level, spot, facing, papon);
        if (vendor.isEmpty()) return fail(c, "Taco Truck template missing.");
        c.getSource().sendSuccess(() -> Component.literal("The Taco Truck is open. Get in line behind Papon."), true);
        return 1;
    }

    private static int serve(CommandContext<CommandSourceStack> c) {
        TacoVendorEntity v = TacoTruckScene.vendorNear(c.getSource().getLevel(), c.getSource().getPosition(), 64);
        if (v == null) return fail(c, "No Taco Truck within 64 blocks.");
        v.hurryUp();
        c.getSource().sendSuccess(() -> Component.literal("Order up."), true);
        return 1;
    }

    private static int fraud(CommandContext<CommandSourceStack> c) {
        TacoVendorEntity v = TacoTruckScene.vendorNear(c.getSource().getLevel(), c.getSource().getPosition(), 64);
        if (v == null) return fail(c, "No Taco Truck within 64 blocks.");
        v.skipToPapon(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.literal("Everyone ahead of Papon has been fed."), true);
        return 1;
    }

    private static int showDistance(CommandContext<CommandSourceStack> c) {
        int min = PaponConfig.JOURNEY_MIN_DISTANCE.get();
        int max = PaponConfig.JOURNEY_MAX_DISTANCE.get();
        c.getSource().sendSuccess(() -> Component.literal("New Taco Truck sites are picked " + min + "-" + max
                + " blocks from where the coupon is handed over."), false);
        return min;
    }

    /**
     * Changes how far away new Taco Truck sites are picked, and saves it to papon-common.toml.
     * Applies to journeys started from now on; a Papon already walking keeps his site (use
     * /papon target to move it).
     */
    private static int setDistance(CommandContext<CommandSourceStack> c, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        PaponConfig.JOURNEY_MIN_DISTANCE.set(Math.max(64, lo));
        PaponConfig.JOURNEY_MAX_DISTANCE.set(Math.max(64, hi));
        PaponConfig.SPEC.save();
        int savedLo = PaponConfig.JOURNEY_MIN_DISTANCE.get();
        int savedHi = PaponConfig.JOURNEY_MAX_DISTANCE.get();
        c.getSource().sendSuccess(() -> Component.literal("Taco Truck distance set to " + savedLo + "-" + savedHi
                + " blocks for new journeys (saved to papon-common.toml)."), true);
        return savedLo;
    }

    private static int target(CommandContext<CommandSourceStack> c, int x, int z) {
        WanderingPaponEntity p = nearest(c, 64);
        if (p == null) return fail(c, "No Wandering Papon within 64 blocks.");
        ServerLevel level = c.getSource().getLevel();
        BlockPos want = new BlockPos(x, level.getSeaLevel(), z);
        BlockPos site = JourneyPlanner.isInlandOnMap(level, x, z) ? want : JourneyPlanner.nearestInland(level, want, p.blockPosition());
        p.setDestination(site);
        String note = site.equals(want) ? "" : " (that is by the water; nearest inland ground instead)";
        c.getSource().sendSuccess(() -> Component.literal("Papon now heads for " + site.getX() + " " + site.getZ() + note + "."), true);
        return 1;
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

    private static int reveal(CommandContext<CommandSourceStack> c) {
        TacoVendorEntity v = TacoTruckScene.vendorNear(c.getSource().getLevel(), c.getSource().getPosition(), 64);
        if (v == null) return fail(c, "No Taco Truck within 64 blocks.");
        v.skipToFraud(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.literal("FRAUDE."), true);
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Vec3 pos = c.getSource().getPosition();
        float yaw = c.getSource().getRotation().y;
        Vec3 ahead = Vec3.directionFromRotation(0, yaw);
        CaravanEntity van = level.getEntitiesOfClass(CaravanEntity.class, new AABB(pos, pos).inflate(40)).stream().findFirst()
                .orElseGet(() -> spawnVan(level, pos.add(new Vec3(-ahead.z, 0, ahead.x).scale(6)), yaw));
        PaponBossEntity boss = TacoBoss.spawnHere(level, pos.add(ahead.scale(8)), yaw + 180, van);
        if (boss == null) return fail(c, "Could not create the boss.");
        c.getSource().sendSuccess(() -> Component.literal("Chef Papon is here."), true);
        return 1;
    }

    private static int van(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        float yaw = c.getSource().getRotation().y;
        Vec3 at = c.getSource().getPosition().add(Vec3.directionFromRotation(0, yaw).scale(5));
        return spawnVan(level, at, yaw) != null ? 1 : fail(c, "No room for a van there.");
    }

    @Nullable
    private static CaravanEntity spawnVan(ServerLevel level, Vec3 at, float yaw) {
        CaravanEntity van = ModEntities.CARAVAN.get().create(level);
        if (van == null) return null;
        van.moveTo(at.x, at.y, at.z, yaw, 0);
        level.addFreshEntity(van);
        return van;
    }

    private static int bossState(CommandContext<CommandSourceStack> c, String name) {
        Vec3 pos = c.getSource().getPosition();
        PaponBossEntity boss = c.getSource().getLevel().getEntitiesOfClass(PaponBossEntity.class, new AABB(pos, pos).inflate(64))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(pos))).orElse(null);
        if (boss == null) return fail(c, "No boss within 64 blocks.");
        BossState s;
        try {
            s = BossState.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fail(c, "Unknown state " + name + ".");
        }
        boss.force(s);
        c.getSource().sendSuccess(() -> Component.literal("Chef Papon is now " + s + "."), true);
        return 1;
    }

    private static int cutaway(CommandContext<CommandSourceStack> c, String name) {
        Vec3 pos = c.getSource().getPosition();
        PaponBossEntity boss = c.getSource().getLevel().getEntitiesOfClass(PaponBossEntity.class, new AABB(pos, pos).inflate(64))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(pos))).orElse(null);
        if (boss == null) return fail(c, "No boss within 64 blocks.");
        PaponBossEntity.Cutaway w;
        try {
            w = PaponBossEntity.Cutaway.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fail(c, "Unknown cutaway " + name + ".");
        }
        boss.devCutaway(w);
        c.getSource().sendSuccess(() -> Component.literal("Cut to the " + w + "."), true);
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
