package org.lantern.internal.pack;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

public final class LanternDynamicPackSource {
    private LanternDynamicPackSource() {
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
            "lantern:dynamic",
            Component.literal("Lantern Dynamic Resources"),
            PackSource.BUILT_IN,
            Optional.empty()
        );
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public LanternDynamicPackResources openPrimary(PackLocationInfo packLocation) {
                return new LanternDynamicPackResources(packLocation);
            }

            @Override
            public LanternDynamicPackResources openFull(
                PackLocationInfo packLocation,
                Pack.Metadata metadata
            ) {
                return new LanternDynamicPackResources(packLocation);
            }
        };
        Pack pack = Objects.requireNonNull(
            Pack.readMetaAndCreate(
                location,
                resources,
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
            ),
            "Lantern dynamic pack metadata is invalid"
        );
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
