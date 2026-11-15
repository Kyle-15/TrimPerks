package com.trimperks;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * ./gradlew checkAssets: parses every generated item definition with the game's own client codec, so a broken JSON
 * fails here instead of showing purple-black tools in someone's game. Not part of the mod jar.
 */
public final class CheckAssets {
	public static void main(String[] args) throws Exception {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		net.minecraft.client.ClientBootstrap.bootstrap();
		int ok = 0, bad = 0;
		try (Stream<Path> files = Files.list(Path.of(args[0]))) {
			for (Path file : (Iterable<Path>) files::iterator) {
				var result = net.minecraft.client.renderer.item.ClientItem.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)));
				if (result.error().isPresent()) {
					bad++;
					System.out.println("BROKEN " + file.getFileName() + ": " + result.error().get().message());
				} else ok++;
			}
		}
		System.out.println("Asset check: " + ok + " OK, " + bad + " broken");
		if (bad > 0) System.exit(1);
	}
}
