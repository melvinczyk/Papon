package com.groid.papon.journey;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension record of every Wandering Papon and every Taco Truck, so admins can find a
 * Papon whose chunk is unloaded and new journeys can head for a truck that already stands.
 * The entity itself is the authority on its journey; this is a mirror it keeps up to date.
 */
public class JourneyData extends SavedData {
    private static final String NAME = "papon_journeys";

    public static final class Journey {
        public final UUID id;
        public BlockPos origin;
        public BlockPos destination;
        public BlockPos lastPos;
        public String state;
        public long lastSeen;

        Journey(UUID id, BlockPos origin, BlockPos destination, BlockPos lastPos, String state, long lastSeen) {
            this.id = id;
            this.origin = origin;
            this.destination = destination;
            this.lastPos = lastPos;
            this.state = state;
            this.lastSeen = lastSeen;
        }
    }

    private final Map<UUID, Journey> journeys = new LinkedHashMap<>();
    private final List<BlockPos> trucks = new ArrayList<>();

    public static JourneyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(JourneyData::load, JourneyData::new, NAME);
    }

    public void track(UUID id, BlockPos origin, BlockPos destination, BlockPos pos, String state, long gameTime) {
        Journey j = journeys.get(id);
        if (j == null) {
            journeys.put(id, new Journey(id, origin, destination, pos, state, gameTime));
        } else {
            j.origin = origin;
            j.destination = destination;
            j.lastPos = pos;
            j.state = state;
            j.lastSeen = gameTime;
        }
        setDirty();
    }

    public void forget(UUID id) { if (journeys.remove(id) != null) setDirty(); }

    public Map<UUID, Journey> journeys() { return Collections.unmodifiableMap(journeys); }

    public void addTruck(BlockPos pos) {
        trucks.add(pos.immutable());
        setDirty();
    }

    public List<BlockPos> trucks() { return Collections.unmodifiableList(trucks); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag js = new ListTag();
        for (Journey j : journeys.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", j.id);
            t.put("Origin", NbtUtils.writeBlockPos(j.origin));
            t.put("Destination", NbtUtils.writeBlockPos(j.destination));
            t.put("LastPos", NbtUtils.writeBlockPos(j.lastPos));
            t.putString("State", j.state);
            t.putLong("LastSeen", j.lastSeen);
            js.add(t);
        }
        tag.put("Journeys", js);
        ListTag ts = new ListTag();
        for (BlockPos p : trucks) ts.add(NbtUtils.writeBlockPos(p));
        tag.put("Trucks", ts);
        return tag;
    }

    private static JourneyData load(CompoundTag tag) {
        JourneyData d = new JourneyData();
        for (Tag e : tag.getList("Journeys", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            UUID id = t.getUUID("Id");
            d.journeys.put(id, new Journey(id, NbtUtils.readBlockPos(t.getCompound("Origin")),
                    NbtUtils.readBlockPos(t.getCompound("Destination")), NbtUtils.readBlockPos(t.getCompound("LastPos")),
                    t.getString("State"), t.getLong("LastSeen")));
        }
        for (Tag e : tag.getList("Trucks", Tag.TAG_COMPOUND)) {
            d.trucks.add(NbtUtils.readBlockPos((CompoundTag) e));
        }
        return d;
    }
}
