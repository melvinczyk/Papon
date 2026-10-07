package com.groid.papon.network;

import com.groid.papon.PaponMod;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The few things entity data cannot carry:
 *   Shake  server -> client  a camera shake at a point (slams, roars, van crashes), Cataclysm style:
 *                            strength falls off with distance from the point
 *   Horn   client -> server  the driver of a van pressed the horn (jump key)
 *   Solo   client -> server  the band's score has gone into a guitar solo (the boss stops to listen), or another
 *                            moment worth a cut to the band (the trumpets coming in, the violins' run...)
 *   QteStart server -> client  Chef Papon has you: the keys to press, a window each (client/QteClient)
 *   QteEnd   server -> client  it is over (broke free, or not)
 *   QteInput client -> server  key n pressed right, or wrong / too late
 *   Fade     server -> client  the screen to black and back (brought back to the Taco Truck after the fight)
 *   OrderTaco client -> server a taco off the reformed chef's menu (client/TacoMenuScreen)
 *   QteResult client -> server a QTE judged as a whole (the squeeze, the duel) is over
 *   QteMeter  client -> server how a running squeeze or duel is going (0..1): he reacts to it, the held player's body too
 */
public final class PaponNetwork {
    private static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(PaponMod.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private PaponNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, Shake.class, Shake::encode, Shake::decode, Shake::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Horn.class, (m, b) -> {}, b -> new Horn(), Horn::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, QteStart.class, QteStart::encode, QteStart::decode, QteStart::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, QteEnd.class, (m, b) -> b.writeBoolean(m.won()), b -> new QteEnd(b.readBoolean()), QteEnd::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, QteInput.class, QteInput::encode, QteInput::decode, QteInput::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, Solo.class, (m, b) -> {
            b.writeVarInt(m.bossId());
            b.writeVarInt(m.kind());
        }, b -> new Solo(b.readVarInt(), b.readVarInt()), Solo::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, Fade.class, Fade::encode, Fade::decode, Fade::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7, OrderTaco.class, (m, b) -> {
            b.writeVarInt(m.vendorId());
            b.writeVarInt(m.taco());
        }, b -> new OrderTaco(b.readVarInt(), b.readVarInt()), OrderTaco::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(8, QteResult.class, QteResult::encode, QteResult::decode, QteResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9, QteMeter.class, (m, b) -> {
            b.writeVarInt(m.bossId());
            b.writeFloat(m.value());
        }, b -> new QteMeter(b.readVarInt(), b.readFloat()), QteMeter::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(10, Struggle.class, (m, b) -> {}, b -> new Struggle(), Struggle::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(11, PillowSwap.class, (m, b) -> {}, b -> new PillowSwap(), PillowSwap::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** From the client: how the squeeze (its meter) or the duel (its rock meter) is going right now, 0..1. */
    public static void qteMeter(int bossId, float value) {
        CHANNEL.sendToServer(new QteMeter(bossId, value));
    }

    public record QteMeter(int bossId, float value) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.level().getEntity(bossId) instanceof com.groid.papon.boss.PaponBossEntity boss) {
                    boss.qteMeter(p, net.minecraft.util.Mth.clamp(value, 0F, 1F));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** The screen fades to black over {@code in} ticks, stays black {@code hold}, and fades back over {@code out}. */
    public static void fade(ServerPlayer p, int in, int hold, int out) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Fade(in, hold, out));
    }

    public record Fade(int in, int hold, int out) {
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(in);
            b.writeVarInt(hold);
            b.writeVarInt(out);
        }

        static Fade decode(FriendlyByteBuf b) {
            return new Fade(b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.groid.papon.client.ScreenFade.start(in, hold, out)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** From the client: taco {@code taco} off the menu of the friendly chef {@code vendorId}. */
    public static void orderTaco(int vendorId, int taco) {
        CHANNEL.sendToServer(new OrderTaco(vendorId, taco));
    }

    public record OrderTaco(int vendorId, int taco) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.level().getEntity(vendorId) instanceof com.groid.papon.entity.npc.TacoVendorEntity v) v.order(p, taco);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** From the client: the band has just gone into a guitar solo (the score is the client's). */
    public static void solo(int bossId) {
        CHANNEL.sendToServer(new Solo(bossId, 0));
    }

    /** From the client: a moment in the band's music worth a cut to them (PaponBossEntity.Cutaway: the trumpets coming in...). */
    public static void cutaway(int bossId, int kind) {
        CHANNEL.sendToServer(new Solo(bossId, kind));
    }

    /** {@code kind} 0: the guitar solo; otherwise a PaponBossEntity.Cutaway. */
    public record Solo(int bossId, int kind) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.level().getEntity(bossId) instanceof com.groid.papon.boss.PaponBossEntity boss
                        && boss.distanceToSqr(p) < 96 * 96) {
                    if (kind == 0) boss.soloHeard();
                    else boss.cutawayHeard(kind);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static void qteStart(ServerPlayer p, int bossId, int kind, int[] keys, int window) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new QteStart(bossId, kind, keys, window));
    }

    public static void qteEnd(ServerPlayer p, boolean won) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new QteEnd(won));
    }

    /** From the client: key {@code step} of the QTE pressed right ({@code ok}) or not. */
    public static void qteInput(int bossId, int step, boolean ok) {
        CHANNEL.sendToServer(new QteInput(bossId, step, ok));
    }

    /** From the client: a QTE that is judged as a whole (the spam meter, the guitar duel) is over; {@code score} 0..100. */
    public static void qteResult(int bossId, int kind, boolean won, int score) {
        CHANNEL.sendToServer(new QteResult(bossId, kind, won, score));
    }

    public record QteResult(int bossId, int kind, boolean won, int score) {
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(bossId);
            b.writeVarInt(kind);
            b.writeBoolean(won);
            b.writeVarInt(score);
        }

        static QteResult decode(FriendlyByteBuf b) {
            return new QteResult(b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.level().getEntity(bossId) instanceof com.groid.papon.boss.PaponBossEntity boss) {
                    boss.qteResult(p, kind, won, score);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record QteStart(int bossId, int kind, int[] keys, int window) {
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(bossId);
            b.writeVarInt(kind);
            b.writeVarIntArray(keys);
            b.writeVarInt(window);
        }

        static QteStart decode(FriendlyByteBuf b) {
            return new QteStart(b.readVarInt(), b.readVarInt(), b.readVarIntArray(16), b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.groid.papon.client.QteClient.start(bossId, kind, keys, window)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record QteEnd(boolean won) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.groid.papon.client.QteClient.end(won)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record QteInput(int bossId, int step, boolean ok) {
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(bossId);
            b.writeVarInt(step);
            b.writeBoolean(ok);
        }

        static QteInput decode(FriendlyByteBuf b) {
            return new QteInput(b.readVarInt(), b.readVarInt(), b.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.level().getEntity(bossId) instanceof com.groid.papon.boss.PaponBossEntity boss) { boss.qteInput(p, step, ok); }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Shakes the camera of everyone within {@code radius * 2} of {@code at}. */
    public static void shake(ServerLevel level, Vec3 at, float radius, float magnitude, int ticks) {
        Shake msg = new Shake(at.x, at.y, at.z, radius, magnitude, ticks);
        double reach = radius * 2;
        for (ServerPlayer p : level.players()) { if (p.distanceToSqr(at) < reach * reach) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg); }
    }

    /** A shake for one player only (centred on them): a hit that lands on them, not a quake. */
    public static void shake(ServerPlayer player, float magnitude, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Shake(player.getX(), player.getY(), player.getZ(), 4, magnitude, ticks));
    }

    public record Shake(double x, double y, double z, float radius, float magnitude, int ticks) {
        void encode(FriendlyByteBuf b) {
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeFloat(radius);
            b.writeFloat(magnitude);
            b.writeVarInt(ticks);
        }

        static Shake decode(FriendlyByteBuf b) {
            return new Shake(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.groid.papon.client.ScreenShake.add(new Vec3(x, y, z), radius, magnitude, ticks)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record Horn() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.getVehicle() instanceof CaravanEntity van && van.getControllingPassenger() == p) { van.honk(); }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** A press of jump from a player in El Gigante's fist (La Mano): struggling to get out of it. */
    public record Struggle() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.getVehicle() instanceof com.groid.papon.boss.SpiritGiantEntity g) g.struggle(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** A press of jump at the body pillow's feet: lift them to your face, or lay them down again (BodyPillowEntity.swap). */
    public record PillowSwap() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.getVehicle() instanceof com.groid.papon.entity.BodyPillowEntity b) b.swap(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
