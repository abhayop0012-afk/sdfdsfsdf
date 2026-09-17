package com.riftcore;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.net.URISyntaxException;
import java.io.IOException;

public final class RiftCoreBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
                    try {
                        event.registrar().discoverPack(
                                RiftCoreBootstrap.class.getResource("/pause-screen-pack").toURI(),
                                "provided"
                        );
                    } catch (URISyntaxException | IOException exception) {
                        throw new IllegalStateException("Unable to register the RiftCore data pack", exception);
                    }
                })
        );
    }
}