package com.nzothr.emcstoragebridge.core;

import java.util.Optional;
import java.util.UUID;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ProjectEAccountService {
    private ProjectEAccountService() {
    }

    public static Optional<IKnowledgeProvider> getReadableAccount(UUID owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || owner == null) {
            return Optional.empty();
        }

        return Optional.of(ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner));
    }

    public static Optional<OnlineAccount> getWritableAccount(UUID owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || owner == null) {
            return Optional.empty();
        }

        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return Optional.empty();
        }

        IKnowledgeProvider knowledge = ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner);
        return Optional.of(new OnlineAccount(player, knowledge));
    }

    public record OnlineAccount(ServerPlayer player, IKnowledgeProvider knowledge) {
    }
}
