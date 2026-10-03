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
 */
public final class PaponNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(PaponMod.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private PaponNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, Shake.class, Shake::encode, Shake::decode, Shake::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Horn.class, (m, b) -> {}, b -> new Horn(), Horn::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Shakes the camera of everyone within {@code radius * 2} of {@code at}. */
    public static void shake(ServerLevel level, Vec3 at, float radius, float magnitude, int ticks) {
        Shake msg = new Shake(at.x, at.y, at.z, radius, magnitude, ticks);
        double reach = radius * 2;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) < reach * reach) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
        }
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
                if (p != null && p.getVehicle() instanceof CaravanEntity van && van.getControllingPassenger() == p) {
                    van.honk();
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
