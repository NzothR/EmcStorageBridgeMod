package com.nzothr.emcstoragebridge.core;

import java.util.Optional;
import java.util.UUID;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
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

        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            // ProjectE's UUID proxy assumes an online player's capability is present and throws
            // when queried while the player entity is being removed after death.
            if (!player.isAlive() || player.isRemoved()) return Optional.empty();
            return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve();
        }

        return Optional.of(ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner));
    }

    public static Optional<OnlineAccount> getWritableAccount(UUID owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || owner == null) {
            return Optional.empty();
        }

        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null || !player.isAlive() || player.isRemoved()) {
            return Optional.empty();
        }

        return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)
                .resolve()
                .map(knowledge -> new OnlineAccount(player, knowledge));
    }

    public record OnlineAccount(ServerPlayer player, IKnowledgeProvider knowledge) {
    }
}
