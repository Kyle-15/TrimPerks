package com.trimperks;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Offers players the tool-trim resource pack when they join, so the server owner never has to put it in
 * server.properties. The link and sha1 are for the exact pack released with this jar (trimperks-pack.properties,
 * written by the build). It's optional for players, sits alongside any other server pack (its own id), and ops can
 * turn it off with general.offer_pack = 0.
 */
public final class PackOffer {
	private static final UUID ID = UUID.nameUUIDFromBytes("trimperks:tool-trims".getBytes(StandardCharsets.UTF_8));
	private static String url, sha1;

	static void register() {
		Properties info = new Properties();
		try (InputStream in = PackOffer.class.getResourceAsStream("/trimperks-pack.properties")) {
			if (in == null) throw new IllegalStateException("missing from the jar");
			info.load(in);
			url = "https://github.com/Kyle-15/TrimPerks/releases/download/v" + info.getProperty("version") + "/TrimPerks-Resources.zip";
			sha1 = info.getProperty("sha1");
		} catch (Exception e) {
			TrimPerks.LOG.warn("No resource pack info in the jar ({}); tool trim textures won't be offered", e.getMessage());
			return;
		}
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (TrimPerks.config.get("general.offer_pack") < 0.5) return;
			handler.send(new ClientboundResourcePackPushPacket(ID, url, sha1, false,
				Optional.of(Component.literal("TrimPerks: shows trims on tools and weapons. Optional."))));
		});
	}

	private PackOffer() {}
}
