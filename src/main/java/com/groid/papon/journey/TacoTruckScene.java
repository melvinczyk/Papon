package com.groid.papon.journey;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.Customer;
import com.groid.papon.entity.npc.GerbinEntity;
import com.groid.papon.vehicle.CaravanEntity;
import com.groid.papon.entity.npc.TacoCustomerEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
import com.groid.papon.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Opens the Taco Truck for business: the truck, the Papon at its window, and a line of
 * customers already waiting. The truck stands ahead of the arrival spot far enough that the
 * whole line fits between them, so the Wandering Papon only has to step into the last place and
 * the player in behind him.
 */
public final class TacoTruckScene {
    private TacoTruckScene() {}

    /** People in line ahead of the Wandering Papon: the four regulars plus some Papons. */
    public static int customerCount() {
        return Customer.values().length - 1 + PaponConfig.EXTRA_PAPONS.get();
    }

    /** Blocks from the arrival spot to the ordering window. */
    public static int windowGap() {
        return (int) Math.ceil(customerCount() * TacoLine.SPACING);
    }

    /**
     * Places the truck {@link #windowGap()} blocks along {@code facing} from {@code spot}, its window
     * facing back, opens it, and queues {@code wanderer} (if any) behind the customers.
     */
    public static Optional<TacoVendorEntity> open(ServerLevel level, BlockPos spot, Direction facing,
                                                  @Nullable WanderingPaponEntity wanderer) {
        int n = customerCount();
        BlockPos window = spot.relative(facing, windowGap());
        int lane = (int) Math.ceil((n + 3) * TacoLine.SPACING) + 3;
        Optional<TacoTruck.Placement> placed = TacoTruck.place(level, window, facing.getOpposite(), lane);
        if (placed.isEmpty()) return Optional.empty();
        TacoTruck.Placement p = placed.get();
        JourneyData.get(level).addTruck(spot);

        TacoVendorEntity vendor = ModEntities.TACO_VENDOR.get().create(level);
        if (vendor == null) return Optional.empty();
        vendor.moveTo(p.vendorPos().x, p.vendorPos().y, p.vendorPos().z, p.vendorYaw(), 0);
        vendor.setYHeadRot(p.vendorYaw());
        vendor.setYBodyRot(p.vendorYaw());
        level.addFreshEntity(vendor);

        List<TacoCustomerEntity> customers = new ArrayList<>();
        List<Customer> order = lineUp(level.getRandom(), PaponConfig.EXTRA_PAPONS.get());
        float faceTruck = facing.toYRot();
        for (int i = 0; i < order.size(); i++) {
            TacoCustomerEntity c = typeOf(order.get(i)).create(level);
            if (c == null) continue;
            Vec3 at = p.line().slot(i);
            c.moveTo(at.x, at.y, at.z, faceTruck, 0);
            c.setYHeadRot(faceTruck);
            c.setYBodyRot(faceTruck);
            level.addFreshEntity(c);
            c.setLineSlot(vendor.getUUID(), p.line(), i);
            customers.add(c);
            level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.8, at.z, 10, 0.3, 0.5, 0.3, 0.02);
        }
        vendor.open(p.line(), customers);
        ensureGerbin(level, p.line());
        parkVan(level, p.line());
        if (wanderer != null) {
            vendor.join(wanderer);
            wanderer.setLineSlot(vendor.getUUID(), p.line(), customers.size(), 0, customers.size() + 1);
        }
        PaponMod.LOG.info("Taco Truck open at {} with {} in line", window, customers.size());
        return Optional.of(vendor);
    }

    /** Hires Gerbin for this truck, unless he is already in it. */
    public static void ensureGerbin(ServerLevel level, TacoLine line) {
        TacoTruck.Station s = TacoTruck.gerbinChop(line);
        if (!level.getEntitiesOfClass(GerbinEntity.class, new AABB(s.pos(), s.pos()).inflate(12)).isEmpty()) return;
        GerbinEntity g = ModEntities.GERBIN.get().create(level);
        if (g == null) return;
        g.moveTo(s.pos().x, s.pos().y, s.pos().z, s.yaw(), 0);
        g.setYHeadRot(s.yaw());
        g.setYBodyRot(s.yaw());
        level.addFreshEntity(g);
        g.hire(line);
    }

    /**
     * Chef Papon's van, parked beside the truck, unless one is already about. Returns it (or null
     * when there is no room for it).
     */
    @Nullable
    public static CaravanEntity parkVan(ServerLevel level, TacoLine line) {
        TacoTruck.Station s = TacoTruck.parking(line);
        List<CaravanEntity> near = level.getEntitiesOfClass(CaravanEntity.class, new AABB(s.pos(), s.pos()).inflate(48));
        if (!near.isEmpty()) return near.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(s.pos()))).orElse(null);
        CaravanEntity van = ModEntities.CARAVAN.get().create(level);
        if (van == null) return null;
        // the pad beside the cab first; failing that, out along the lane
        Vec3[] tries = {s.pos(), line.slot(0).add(Vec3.atLowerCornerOf(line.out().getNormal()).scale(12))
                .add(Vec3.atLowerCornerOf(line.side().getNormal()).scale(-5)), line.slot(0).add(Vec3.atLowerCornerOf(line.out().getNormal()).scale(18))};
        for (Vec3 at : tries) {
            van.moveTo(at.x, at.y, at.z, s.yaw(), 0);
            if (level.noCollision(van)) {
                level.addFreshEntity(van);
                return van;
            }
        }
        PaponMod.LOG.warn("No room to park the van at the Taco Truck {}", line.window());
        return null;
    }

    /**
     * The four regulars in a random order with the Papons slipped in between them, never two
     * Papons together while there are gaps left, so the famous faces are spread down the line.
     */
    public static List<Customer> lineUp(RandomSource random, int papons) {
        List<Customer> stars = new ArrayList<>(List.of(Customer.DON_POLLO, Customer.EDP445, Customer.BADLANDS_CHUGS,
                Customer.REVIEWBRAH));
        Collections.shuffle(stars, new java.util.Random(random.nextLong()));
        int gaps = stars.size() + 1;
        int[] perGap = new int[gaps];
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < papons; i++) {
            if (free.isEmpty()) for (int g = 0; g < gaps; g++) free.add(g);
            perGap[free.remove(random.nextInt(free.size()))]++;
        }
        List<Customer> out = new ArrayList<>();
        for (int g = 0; g < gaps; g++) {
            for (int k = 0; k < perGap[g]; k++) out.add(Customer.PAPON);
            if (g < stars.size()) out.add(stars.get(g));
        }
        return out;
    }

    public static EntityType<TacoCustomerEntity> typeOf(Customer c) {
        return switch (c) {
            case DON_POLLO -> ModEntities.DON_POLLO.get();
            case EDP445 -> ModEntities.EDP445.get();
            case BADLANDS_CHUGS -> ModEntities.BADLANDS_CHUGS.get();
            case REVIEWBRAH -> ModEntities.REVIEWBRAH.get();
            case PAPON -> ModEntities.CUSTOMER_PAPON.get();
        };
    }

    /** The nearest truck vendor within {@code radius}, if any. */
    @Nullable
    public static TacoVendorEntity vendorNear(ServerLevel level, Vec3 pos, double radius) {
        return level.getEntitiesOfClass(TacoVendorEntity.class, new AABB(pos, pos).inflate(radius)).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(pos))).orElse(null);
    }
}
