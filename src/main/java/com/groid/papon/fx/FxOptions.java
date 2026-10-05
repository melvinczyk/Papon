package com.groid.papon.fx;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Locale;

/**
 * What every one of the boss fight's particles is told (they all share it; see {@link com.groid.papon.registry.ModParticles}):
 *
 * @param sprite which variant (static types), or -1: animated over its life, or a random variant
 * @param size   in blocks (for decals and rings, the radius)
 * @param grow   size multiplier reached at the end of its life
 * @param color  0xRRGGBB tint (white textures take it whole; coloured ones are multiplied)
 * @param alpha  starting opacity
 * @param life   ticks it lives (a warning lives exactly as long as the wait it warns of)
 * @param yaw    which way a flat one faces (slashes; decals spin from it)
 */
public record FxOptions(ParticleType<FxOptions> type, int sprite, float size, float grow, int color, float alpha, int life,
                        float yaw) implements ParticleOptions {

    @Override
    public ParticleType<?> getType() { return type; }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeByte(sprite);
        buf.writeFloat(size);
        buf.writeFloat(grow);
        buf.writeInt(color);
        buf.writeFloat(alpha);
        buf.writeShort(life);
        buf.writeFloat(yaw);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %d %.2f %.2f %06x %.2f %d %.1f", BuiltInRegistries.PARTICLE_TYPE.getKey(type),
                sprite, size, grow, color, alpha, life, yaw);
    }

    public static Codec<FxOptions> codec(ParticleType<FxOptions> type) {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("sprite").forGetter(FxOptions::sprite),
                Codec.FLOAT.fieldOf("size").forGetter(FxOptions::size),
                Codec.FLOAT.fieldOf("grow").forGetter(FxOptions::grow),
                Codec.INT.fieldOf("color").forGetter(FxOptions::color),
                Codec.FLOAT.fieldOf("alpha").forGetter(FxOptions::alpha),
                Codec.INT.fieldOf("life").forGetter(FxOptions::life),
                Codec.FLOAT.fieldOf("yaw").forGetter(FxOptions::yaw)
        ).apply(i, (s, sz, g, c, a, l, y) -> new FxOptions(type, s, sz, g, c, a, l, y)));
    }

    @SuppressWarnings("deprecation")
    public static final Deserializer<FxOptions> DESERIALIZER = new Deserializer<>() {
        @Override
        public FxOptions fromCommand(ParticleType<FxOptions> type, StringReader r) throws CommandSyntaxException {
            // /particle papon:fire <size> <color hex> <life>
            r.expect(' ');
            float size = r.readFloat();
            r.expect(' ');
            int color = Integer.parseInt(r.readString(), 16);
            r.expect(' ');
            int life = r.readInt();
            return new FxOptions(type, -1, size, 1F, color, 1F, life, 0F);
        }

        @Override
        public FxOptions fromNetwork(ParticleType<FxOptions> type, FriendlyByteBuf buf) {
            return new FxOptions(type, buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat(),
                    buf.readShort(), buf.readFloat());
        }
    };

    public static ParticleType<FxOptions> newType() {
        return new ParticleType<>(true, DESERIALIZER) {
            @Override
            public Codec<FxOptions> codec() { return FxOptions.codec(this); }
        };
    }
}
