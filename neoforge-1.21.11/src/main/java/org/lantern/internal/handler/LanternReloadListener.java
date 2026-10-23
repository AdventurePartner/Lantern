package org.lantern.internal.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public final class LanternReloadListener implements PreparableReloadListener {
    @Override
    public CompletableFuture<Void> reload(
        SharedState sharedState,
        Executor preparationExecutor,
        PreparationBarrier barrier,
        Executor applyExecutor
    ) {
        return barrier.wait(null).thenRunAsync(ResourceHandler.INSTANCE::reload, applyExecutor);
    }
}
