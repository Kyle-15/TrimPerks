package com.trimperks;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown bars: a boss bar at the top of the screen that fills up while a trim cools down, then turns green with a
 * ding when it's ready. Vanilla boss bars, so players don't need the mod. One bar each for armor, weapon and tool.
 * Players can hide them (/trimperks bars off, remembered across restarts); ops can turn them off for everyone
 * (general.cooldown_bars = 0).
 */
public final class Cooldowns {
	public static final AttachmentType<Boolean> HIDDEN = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(TrimPerks.MOD_ID, "hide_cooldown_bars"), b -> b.persistent(Codec.BOOL).copyOnDeath());

	static final class Bar {
		final ServerBossEvent event;
		final String label;
		final long start, end;
		long removeAt = -1;

		Bar(ServerBossEvent event, String label, long start, long end) {
			this.event = event;
			this.label = label;
			this.start = start;
			this.end = end;
		}
	}

	static void init() {} // loads the class so the attachment is registered at startup

	public static boolean shown(ServerPlayer p) {
		return TrimPerks.config.get("general.cooldown_bars") >= 0.5 && !Boolean.TRUE.equals(p.getAttached(HIDDEN));
	}

	public static void setHidden(ServerPlayer p, boolean hidden) {
		if (hidden) p.setAttached(HIDDEN, true);
		else p.removeAttached(HIDDEN);
		if (hidden) clear(p);
	}

	/** Called when a cooldown starts. Short ones (under a second) get no bar. */
	public static void start(Player player, Trims.Active a, long ticks) {
		if (!(player instanceof ServerPlayer p) || ticks < 20 || !shown(p)) return;
		Map<String, Bar> bars = Trims.state(p).bars;
		String kind = a.kind().name().toLowerCase(java.util.Locale.ROOT);
		Bar old = bars.remove(kind);
		if (old != null) old.event.removeAllPlayers();
		String label = Perks.title(a.pattern()) + " " + kind;
		BossEvent.BossBarColor color = switch (a.kind()) {
			case ARMOR -> BossEvent.BossBarColor.BLUE;
			case WEAPON -> BossEvent.BossBarColor.RED;
			default -> BossEvent.BossBarColor.YELLOW;
		};
		long now = Trims.now(p.level());
		ServerBossEvent event = Compat.bossEvent(name(label, ticks), color, BossEvent.BossBarOverlay.PROGRESS);
		event.setProgress(0);
		event.addPlayer(p);
		bars.put(kind, new Bar(event, label, now, now + ticks));
	}

	/** Every tick: fill the bars, show "ready", then remove them. */
	public static void tick(ServerPlayer p, long now) {
		Map<String, Bar> bars = Trims.state(p).bars;
		if (bars.isEmpty()) return;
		if (!shown(p)) {
			clear(p);
			return;
		}
		for (Iterator<Bar> it = bars.values().iterator(); it.hasNext(); ) {
			Bar bar = it.next();
			if (!bar.event.getPlayers().contains(p)) { // rejoined or respawned: a new player object
				bar.event.removeAllPlayers();
				bar.event.addPlayer(p);
			}
			if (now < bar.end) {
				if ((now - bar.start) % 5 == 0) bar.event.setProgress((now - bar.start) / (float) (bar.end - bar.start));
				if ((bar.end - now) % 20 == 0) bar.event.setName(name(bar.label, bar.end - now));
			} else if (bar.removeAt < 0) {
				bar.event.setProgress(1);
				bar.event.setColor(BossEvent.BossBarColor.GREEN);
				bar.event.setName(Component.literal(bar.label + " ready!"));
				p.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS,
					p.getX(), p.getY(), p.getZ(), 0.6f, 1.6f, p.getRandom().nextLong()));
				bar.removeAt = now + 40;
			} else if (now >= bar.removeAt) {
				bar.event.removeAllPlayers();
				it.remove();
			}
		}
	}

	public static void clear(ServerPlayer p) {
		Map<String, Bar> bars = Trims.state(p).bars;
		for (Bar bar : bars.values()) bar.event.removeAllPlayers();
		bars.clear();
	}

	private static Component name(String label, long ticksLeft) {
		return Component.literal(label + ": " + (ticksLeft + 19) / 20 + "s");
	}

	private Cooldowns() {}
}
