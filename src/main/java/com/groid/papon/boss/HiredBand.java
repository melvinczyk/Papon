package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El Requinto's band (item/RequintoItem, HiredMariachiEntity): who has been hired, where each walks, and what their
 * playing does for you.
 *
 * Each strum of El Requinto hires the next of the band in stage order (BandRole: the singer first, nobody plays without
 * him). The roster is the player's (persisted NBT: it survives death and logging off); the members are not saved, and
 * whoever is missing (logged in, another dimension, back from a knockout) walks back in from behind, one at a time.
 *
 * Buffs, refreshed on the band's beat to the owner and every player within {@code buffRange} of them, by who is playing
 * (the singer must be, or nobody does):
 * <pre>
 *   trumpets (3)            Strength         I, II with all three
 *   violins (3)             Regeneration     I, II with all three
 *   guitar                  Haste            I, II with the vihuela too
 *   vihuela                 Speed I
 *   Gordo, the guitarron    Resistance I     (asleep: nothing; he naps when nothing happens for a minute)
 *   coro (3)                Absorption       2 hearts topped up every 10 s, 4 with all three
 *   all thirteen            ¡Mariachi completo!: Strength and Regeneration one level up, Fire Resistance, 2 more
 *                           hearts, the real "Canción del Mariachi" (the singer counts it in), and a grito on every
 *                           kill: the next blow within 5 s lands half again as hard
 * </pre>
 * They will not go anywhere near Chef Papon himself: within 80 blocks of him the band runs off and waits.
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
public final class HiredBand {
    private static final String TAG = "PaponBand";
    private static final double BOSS_RANGE = 80;
    private static final int GRITO_TICKS = 100;
    private static final float GRITO = 1.5F;

    /** Each one's place behind his jefe: (to the side, behind), in blocks. Three rows, the singer up front. */
    private static final double[][] SLOTS = new double[BandRole.values().length][];

    static {
        put(BandRole.SINGER, 0, 2.4);
        put(BandRole.VIOLIN_1, -3.2, 3.8);
        put(BandRole.TRUMPET_1, -1.6, 3.8);
        put(BandRole.TRUMPET_3, 0, 3.9);
        put(BandRole.TRUMPET_2, 1.6, 3.8);
        put(BandRole.VIOLIN_2, 3.2, 3.8);
        put(BandRole.CORO_1, -4.8, 5.4);
        put(BandRole.VIOLIN_3, -3.2, 5.4);
        put(BandRole.GUITAR, -1.6, 5.4);
        put(BandRole.GUITARRON, 0, 5.6);
        put(BandRole.VIHUELA, 1.6, 5.4);
        put(BandRole.CORO_2, 3.2, 5.4);
        put(BandRole.CORO_3, 4.8, 5.4);
    }

    private static void put(BandRole r, double side, double back) { SLOTS[r.ordinal()] = new double[]{side, back}; }

    /** One owner's band as it stands right now (server only). */
    private static final class Crew {
        final EnumMap<BandRole, HiredMariachiEntity> live = new EnumMap<>(BandRole.class);
        final Vec3[] slots = new Vec3[BandRole.values().length];
        Vec3 heading;
        @Nullable Vec3 last;
        double speed;
        long combatUntil, stillSince, lastSpawn, talkAt, lowAt, gritoUntil, scaredUntil, countAt;
        boolean scared, song;
        /** The count-in: 0 none, 1 "¿Listos, muchachos?", 2 "¡LISTOOOS!", 3 counting. */
        int countPhase;
        int songStart;
    }

    private static final Map<UUID, Crew> CREWS = new HashMap<>();

    private HiredBand() {}

    // ------------------------------------------------------------------ the roster (the player's)

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag kept = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!kept.contains(TAG)) kept.put(TAG, new CompoundTag());
        return kept.getCompound(TAG);
    }

    private static int mask(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(TAG).getInt("Hired");
    }

    private static void setMask(Player p, int mask) { data(p).putInt("Hired", mask); }

    private static long koUntil(Player p, BandRole r) {
        long[] a = data(p).getLongArray("KO");
        return a.length > r.ordinal() ? a[r.ordinal()] : 0;
    }

    private static void setKo(Player p, BandRole r, long until) {
        CompoundTag d = data(p);
        long[] a = d.getLongArray("KO");
        if (a.length < BandRole.values().length) a = java.util.Arrays.copyOf(a, BandRole.values().length);
        a[r.ordinal()] = until;
        d.putLongArray("KO", a);
    }

    /** How many of the band {@code p} has hired. */
    public static int hired(Player p) { return Integer.bitCount(mask(p)); }

    private static Crew crew(Player p) {
        return CREWS.computeIfAbsent(p.getUUID(), k -> {
            Crew c = new Crew();
            Vec3 look = p.getLookAngle();
            c.heading = new Vec3(look.x, 0, look.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize();
            c.stillSince = p.level().getGameTime();
            return c;
        });
    }

    // ------------------------------------------------------------------ El Requinto

    /** A strum: the next of the band comes walking in (or, all of them here, a flourish). */
    public static void summon(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Crew c = crew(p);
        long now = level.getGameTime();
        if (boss(level, p) != null) {
            tell(p, "message.papon.band.scared", ChatFormatting.RED);
            return;
        }
        int mask = mask(p);
        BandRole next = null;
        for (BandRole r : BandRole.values()) {
            if ((mask & 1 << r.ordinal()) == 0) {
                next = r;
                break;
            }
        }
        if (next == null) {
            for (HiredMariachiEntity m : c.live.values()) m.cheer(40 + level.random.nextInt(24));
            HiredMariachiEntity singer = c.live.get(BandRole.SINGER);
            if (singer != null && now >= c.talkAt) {
                singer.line("p4_hype_fiesta", 40);
                c.talkAt = now + 100;
            }
            return;
        }
        setMask(p, mask | 1 << next.ordinal());
        setKo(p, next, 0);
        HiredMariachiEntity m = spawn(level, p, c, next);
        if (next.singer()) m.line("ya_voy", 40);
        else if (level.random.nextBoolean()) m.line(new String[]{"p4_hype_andale", "p4_hype_eso", "ya_voy", "p4_ay"}[level.random.nextInt(4)], 36);
        int n = hired(p);
        p.displayClientMessage(Component.translatable("message.papon.band.joined", Component.translatable("band.papon." + next.name().toLowerCase(java.util.Locale.ROOT)), n)
                .withStyle(ChatFormatting.GOLD), true);
        if (n == BandRole.values().length) p.displayClientMessage(Component.translatable("message.papon.band.full").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    /** Sneak + strum: everyone home (a bow, and off they walk). */
    public static void dismiss(ServerPlayer p) {
        Crew c = crew(p);
        if (mask(p) == 0) return;
        setMask(p, 0);
        for (HiredMariachiEntity m : c.live.values()) {
            m.stopSong();
            m.dismiss(p.position());
        }
        c.live.clear();
        c.song = false;
        c.countPhase = 0;
        tell(p, "message.papon.band.dismissed", ChatFormatting.GOLD);
    }

    private static HiredMariachiEntity spawn(ServerLevel level, Player p, Crew c, BandRole r) {
        if (c.slots[r.ordinal()] == null) slots(level, p, c);
        Vec3 slot = slot(p, r);
        // he walks in from behind the band, if there is open ground there; else he is just there
        Vec3 from = slot.subtract(c.heading.scale(7));
        boolean walk = clip(level, p, slot, from).distanceToSqr(from) < 0.01;
        Vec3 at = ground(level, walk ? from : slot);
        if (!walk || Math.abs(at.y - slot.y) > 4) {
            at = ground(level, slot);
            level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.8, at.z, 8, 0.3, 0.5, 0.3, 0.02);
        }
        float yaw = (float) (Mth.atan2(slot.z - at.z, slot.x - at.x) * Mth.RAD_TO_DEG) - 90F;
        HiredMariachiEntity m = HiredMariachiEntity.create(level, p, r, at, yaw);
        c.live.put(r, m);
        c.lastSpawn = level.getGameTime();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
        return m;
    }

    // ------------------------------------------------------------------ the formation

    /** His place behind {@code p} right now. */
    static Vec3 slot(Player p, BandRole r) {
        Crew c = CREWS.get(p.getUUID());
        Vec3 s = c == null ? null : c.slots[r.ordinal()];
        return s != null ? s : p.position();
    }

    /** How fast {@code p} is going, blocks a tick (the band keeps pace). */
    static double ownerSpeed(Player p) {
        Crew c = CREWS.get(p.getUUID());
        return c == null ? 0 : c.speed;
    }

    /** Playing (not just waiting, instruments ready): in a fight, or the whole band's song. */
    static boolean performing(Player p) {
        Crew c = CREWS.get(p.getUUID());
        return c != null && (c.song || p.level().getGameTime() < c.combatUntil);
    }

    /** Rows behind him along the way he is heading; each place pulled in out of walls (corridors bunch them up, never in rock). */
    private static void slots(ServerLevel level, Player p, Crew c) {
        Vec3 h = c.heading;
        Vec3 side = new Vec3(-h.z, 0, h.x);
        Vec3 o = p.position();
        for (BandRole r : BandRole.values()) {
            double[] off = SLOTS[r.ordinal()];
            Vec3 centre = clip(level, p, o, o.subtract(h.scale(off[1])));
            c.slots[r.ordinal()] = clip(level, p, centre, centre.add(side.scale(off[0])));
        }
    }

    /** {@code to}, or short of the first block in the way from {@code from}. */
    private static Vec3 clip(ServerLevel level, Entity by, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from.add(0, 0.6, 0), to.add(0, 0.6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, by));
        if (hit.getType() != HitResult.Type.BLOCK) return to;
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4) return from;
        double k = Math.max(0, hit.getLocation().subtract(from.add(0, 0.6, 0)).length() - 0.6) / len;
        return from.add(d.scale(k));
    }

    /** Somewhere to stand at {@code at}: a solid block under two free ones, looked for a little above and below. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 3; dy >= -6; dy--) {
            BlockPos feet = base.offset(0, dy, 0);
            if (!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return new Vec3(at.x, feet.getY(), at.z);
            }
        }
        return at;
    }

    // ------------------------------------------------------------------ every tick

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        int mask = mask(p);
        if (mask == 0) {
            Crew gone = CREWS.get(p.getUUID());
            if (gone != null && gone.live.isEmpty()) CREWS.remove(p.getUUID());
            return;
        }
        ServerLevel level = p.serverLevel();
        Crew c = crew(p);
        long now = level.getGameTime();
        // the way he is going (the band's rows face it; standing still, they stay put while he looks about)
        Vec3 pos = p.position();
        if (c.last != null) {
            double mx = pos.x - c.last.x, mz = pos.z - c.last.z;
            double hs = Math.sqrt(mx * mx + mz * mz);
            if (hs > 4) hs = 0;             // a teleport, not a step
            c.speed = c.speed * 0.7 + hs * 0.3;
            if (hs > 0.04) {
                Vec3 dir = new Vec3(mx / hs, 0, mz / hs);
                Vec3 h = c.heading.lerp(dir, 0.12);
                if (h.lengthSqr() > 1.0E-4) c.heading = h.normalize();
                c.stillSince = now;
            }
        }
        c.last = pos;
        if (now % 4 == 0) slots(level, p, c);
        c.live.values().removeIf(m -> m.isRemoved() || m.leaving() || m.level() != level);
        if (!p.isAlive() || p.isSpectator() || now % 10 != 0) return;

        PaponBossEntity boss = boss(level, p);
        if (boss != null) {
            // "Este señor está LOCO...": not one of them goes near him
            if (!c.scared && !c.live.isEmpty()) {
                boolean first = true;
                for (HiredMariachiEntity m : c.live.values()) {
                    m.stopSong();
                    m.flee(boss.position(), first ? "p3_loco" : null);
                    first = false;
                }
                tell(p, "message.papon.band.scared", ChatFormatting.RED);
            }
            c.scared = true;
            c.scaredUntil = now + 200;
            c.live.clear();
            c.song = false;
            c.countPhase = 0;
            return;
        }
        if (c.scared && now < c.scaredUntil) return;
        c.scared = false;

        // whoever is missing (just logged in, a new dimension, back from a knockout) walks back in, one at a time
        if (now - c.lastSpawn >= 10) {
            for (BandRole r : BandRole.values()) {
                if ((mask & 1 << r.ordinal()) != 0 && !c.live.containsKey(r) && koUntil(p, r) <= now) {
                    spawn(level, p, c, r);
                    break;
                }
            }
        }
        // Gordo naps when nothing happens for a minute (and the whole band's song falls apart without its bass)
        HiredMariachiEntity gordo = c.live.get(BandRole.GUITARRON);
        if (gordo != null) {
            boolean busy = now <= c.combatUntil || now - c.stillSince < 40;
            if (!busy && now > c.combatUntil + 1200 && now - c.stillSince > 1200) gordo.doze();
            else if (busy) gordo.rouse();
        }
        song(level, c, now);
        if (p.getHealth() < p.getMaxHealth() * 0.3F && now >= c.lowAt && !c.live.isEmpty()) {
            anyone(c, level).line(level.random.nextBoolean() ? "p3_mama" : "ay_dios", 36);
            c.lowAt = now + 600;
        }
        if (now % 20 == 0 && PaponConfig.REQUINTO_BUFFS.get()) buffs(level, p, c, now);
    }

    /** All thirteen playing: "¿Listos, muchachos?" "¡LISTOOOS!", the count, and the song; anyone missing and it stops. */
    private static void song(ServerLevel level, Crew c, long now) {
        int on = 0;
        for (HiredMariachiEntity m : c.live.values()) if (m.playing()) on++;
        HiredMariachiEntity singer = c.live.get(BandRole.SINGER);
        if (on < BandRole.values().length || singer == null) {
            if (c.song || c.countPhase != 0) {
                for (HiredMariachiEntity m : c.live.values()) m.stopSong();
                c.song = false;
                c.countPhase = 0;
            }
            return;
        }
        if (c.song) return;
        switch (c.countPhase) {
            case 0 -> {
                singer.line("p4_hype_ready", 40);
                c.countPhase = 1;
                c.countAt = now + 40;
            }
            case 1 -> {
                if (now < c.countAt) return;
                for (BandRole r : new BandRole[]{BandRole.CORO_1, BandRole.CORO_2, BandRole.CORO_3}) c.live.get(r).line("p4_hype_ready_a", 30);
                c.countPhase = 2;
                c.countAt = now + 30;
            }
            case 2 -> {
                if (now < c.countAt) return;
                c.songStart = (int) ((now + BandState.COUNT.duration - 1) & 0x7FFFFFFF);
                for (HiredMariachiEntity m : c.live.values()) m.startCount(c.songStart);
                c.countPhase = 3;
                c.countAt = now + BandState.COUNT.duration - 1;
            }
            default -> {
                if (now < c.countAt) return;
                for (HiredMariachiEntity m : c.live.values()) m.startSong(c.songStart);
                c.song = true;
                c.countPhase = 0;
            }
        }
    }

    private static void buffs(ServerLevel level, ServerPlayer p, Crew c, long now) {
        int trumpets = 0, violins = 0, coro = 0;
        boolean singer = false, guitar = false, vihuela = false, gordo = false;
        int on = 0;
        for (HiredMariachiEntity m : c.live.values()) {
            if (!m.playing() || m.distanceToSqr(p) > 20 * 20) continue;
            on++;
            switch (m.getRole()) {
                case SINGER -> singer = true;
                case TRUMPET_1, TRUMPET_2, TRUMPET_3 -> trumpets++;
                case VIOLIN_1, VIOLIN_2, VIOLIN_3 -> violins++;
                case GUITAR -> guitar = true;
                case VIHUELA -> vihuela = true;
                case GUITARRON -> gordo = true;
                case CORO_1, CORO_2, CORO_3 -> coro++;
            }
        }
        if (!singer) return;
        boolean full = on == BandRole.values().length;
        int strength = trumpets == 0 ? -1 : (trumpets == 3 ? 1 : 0) + (full ? 1 : 0);
        int regen = violins == 0 ? -1 : (violins == 3 ? 1 : 0) + (full ? 1 : 0);
        int haste = guitar ? (vihuela ? 1 : 0) : -1;
        int speed = vihuela ? 0 : -1;
        int resist = gordo ? 0 : -1;
        float hearts = (coro == 0 ? 0 : coro == 3 ? 8 : 4) + (full ? 4 : 0);
        double range = PaponConfig.REQUINTO_RANGE.get();
        List<Player> who = new ArrayList<>();
        who.add(p);
        for (Player o : level.players()) if (o != p && !o.isSpectator() && o.isAlive() && o.distanceToSqr(p) < range * range) who.add(o);
        for (Player t : who) {
            give(t, MobEffects.DAMAGE_BOOST, strength);
            give(t, MobEffects.REGENERATION, regen);
            give(t, MobEffects.DIG_SPEED, haste);
            give(t, MobEffects.MOVEMENT_SPEED, speed);
            give(t, MobEffects.DAMAGE_RESISTANCE, resist);
            if (full) give(t, MobEffects.FIRE_RESISTANCE, 0);
            // the coro's backup hearts, topped up every 10 s
            if (hearts > 0 && now % 200 == 0 && t.getAbsorptionAmount() < hearts) {
                t.setAbsorptionAmount(hearts);
                level.sendParticles(ParticleTypes.NOTE, t.getX(), t.getY() + 2.1, t.getZ(), 3, 0.4, 0.1, 0.4, 1);
            }
        }
    }

    /**
     * Keeps {@code effect} on at {@code amp} (-1: leave it be). Topped up only when nearly out, so its timer runs through
     * Regeneration's healing ticks (refreshed every second to the same value it would never heal).
     */
    private static void give(LivingEntity e, MobEffect effect, int amp) {
        if (amp < 0) return;
        MobEffectInstance had = e.getEffect(effect);
        if (had == null || had.getAmplifier() < amp || had.getDuration() < 30) e.addEffect(new MobEffectInstance(effect, 120, amp, true, false, true));
    }

    // ------------------------------------------------------------------ what happens around them

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        long now = victim.level().getGameTime();
        Crew hurt = victim instanceof Player ? CREWS.get(victim.getUUID()) : null;
        if (hurt != null) hurt.combatUntil = now + 200;
        if (event.getSource().getEntity() instanceof ServerPlayer by && !(victim instanceof Player)) {
            Crew c = CREWS.get(by.getUUID());
            if (c == null) return;
            c.combatUntil = now + 200;
            if (now < c.gritoUntil) {
                // the grito: the band shouts with the last kill, and this one lands half again as hard
                event.setAmount(event.getAmount() * GRITO);
                c.gritoUntil = 0;
                ((ServerLevel) victim.level()).sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        14, 0.4, 0.4, 0.4, 0.3);
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.9F);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer by)) return;
        Crew c = CREWS.get(by.getUUID());
        if (c == null || c.live.isEmpty()) return;
        long now = level.getGameTime();
        if (victim.getType().is(Tags.EntityTypes.BOSSES)) {
            // something big down: the whole band jumps about
            for (HiredMariachiEntity m : c.live.values()) m.cheer(64 + level.random.nextInt(20));
            HiredMariachiEntity singer = c.live.get(BandRole.SINGER);
            if (singer != null) singer.line("p4_hype_epic", 50);
            c.talkAt = now + 120;
            return;
        }
        if (!(victim instanceof Enemy)) return;
        int on = 0;
        for (HiredMariachiEntity m : c.live.values()) if (m.playing()) on++;
        if (on < BandRole.values().length) return;
        c.gritoUntil = now + GRITO_TICKS;
        if (now >= c.talkAt) {
            anyone(c, level).line(new String[]{"p4_hype_eso", "p4_hype_andale", "p4_ay"}[level.random.nextInt(3)], 34);
            c.talkAt = now + 120;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { CREWS.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Crew c = CREWS.get(event.getEntity().getUUID());
        if (c != null) {
            c.live.clear();
            c.last = null;
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) { CREWS.clear(); }

    /** One of the band ran off ({@code knockoutSeconds} until he is back). */
    static void knockedOut(ServerLevel level, UUID owner, BandRole role) {
        Player p = level.getPlayerByUUID(owner);
        if (p == null) return;
        setKo(p, role, level.getGameTime() + PaponConfig.REQUINTO_KO_SECONDS.get() * 20L);
        Crew c = CREWS.get(owner);
        if (c != null) c.live.remove(role);
        p.displayClientMessage(Component.translatable("message.papon.band.knocked", Component.translatable("band.papon." + role.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.RED), true);
    }

    @Nullable
    private static PaponBossEntity boss(ServerLevel level, Player p) {
        List<PaponBossEntity> l = level.getEntitiesOfClass(PaponBossEntity.class, p.getBoundingBox().inflate(BOSS_RANGE), Entity::isAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    private static HiredMariachiEntity anyone(Crew c, ServerLevel level) {
        List<HiredMariachiEntity> l = new ArrayList<>(c.live.values());
        return l.get(level.random.nextInt(l.size()));
    }

    private static void tell(Player p, String key, ChatFormatting style) { p.displayClientMessage(Component.translatable(key).withStyle(style), true); }

    /** Every one of the band at once (/papon band all, the autoshot). */
    public static void hireAll(ServerPlayer p) {
        setMask(p, (1 << BandRole.values().length) - 1);
        for (BandRole r : BandRole.values()) setKo(p, r, 0);
        Crew c = crew(p);
        slots(p.serverLevel(), p, c);
        for (BandRole r : BandRole.values()) if (!c.live.containsKey(r)) spawn(p.serverLevel(), p, c, r);
    }
}
