/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2023, rdutta <https://github.com/rdutta>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package nr.gauntlet.module.boss;

import nr.gauntlet.module.Module;
import nr.gauntlet.module.history.RunHistoryManager;
import nr.gauntlet.module.history.StatsTracker;
import nr.gauntlet.module.overlay.TimerOverlay;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.NullNpcID;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import java.util.Arrays;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@Singleton
public final class BossModule implements Module
{
	private static final List<Integer> HUNLLEF_IDS = List.of(
		NpcID.CRYSTALLINE_HUNLLEF,
		NpcID.CRYSTALLINE_HUNLLEF_9022,
		NpcID.CRYSTALLINE_HUNLLEF_9023,
		NpcID.CRYSTALLINE_HUNLLEF_9024,
		NpcID.CORRUPTED_HUNLLEF,
		NpcID.CORRUPTED_HUNLLEF_9036,
		NpcID.CORRUPTED_HUNLLEF_9037,
		NpcID.CORRUPTED_HUNLLEF_9038
	);

	private static final List<Integer> TORNADO_IDS = List.of(NullNpcID.NULL_9025, NullNpcID.NULL_9039, 14142); // 14142 is echo tornado, remove after leagues

	// Animation Ids for tracking
	private static final int PLAYER_MAGE_ATTACK_ID = 1167;
	private static final int PLAYER_RANGE_ATTACK_ID = 426;
	private static final int PLAYER_MELEE_ATTACK_ID = 428;
	private static final int PLAYER_MELEE_ALT_ATTACK_ID = 440;
	private static final int PLAYER_SCEPTRE_ATTACK_ID = 401;
	private static final int PLAYER_KICK_ATTACK_ID = 423;
	private static final int PLAYER_PUNCH_ATTACK_ID = 422;
	private static final int[] PLAYER_ATTACK_ANIMATION_IDS = {
		PLAYER_MAGE_ATTACK_ID,
		PLAYER_MELEE_ATTACK_ID,
		PLAYER_RANGE_ATTACK_ID,
		PLAYER_KICK_ATTACK_ID,
		PLAYER_PUNCH_ATTACK_ID,
		PLAYER_MELEE_ALT_ATTACK_ID,
		PLAYER_SCEPTRE_ATTACK_ID
	};

	private static final int BOSS_ATTACK_ANIMATION_ID = 8419;
	private static final int BOSS_STOMP_ANIMATION_ID = 8420;
	private static final int BOSS_SWITCH_TO_MAGE_ANIMATION_ID = 8754;
	private static final int BOSS_SWITCH_TO_RANGE_ANIMATION_ID = 8755;

	// Ground object ids
	private static final int DAMAGE_TILE_ID = 36048;

	// Timings
	private static final int WEAPON_ATTACK_SPEED = 4;
	private static final int SCEPTRE_ATTACK_SPEED = 5;

	@Getter(AccessLevel.PACKAGE)
	private final List<NPC> tornadoes = new ArrayList<>();

	@Inject
	private EventBus eventBus;
	@Inject
	private Client client;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private TimerOverlay timerOverlay;
	@Inject
	private BossOverlay bossOverlay;
	@Inject
	private StatsTracker statsTracker;
	@Inject
	private RunHistoryManager historyManager;

	@Nullable
	@Getter(AccessLevel.PACKAGE)
	private NPC hunllef;

	private boolean isCorrupted;
	private boolean inBossFight;
	private static final int MIN_TICKS_FOR_VALID_RUN = 100; // Filter out immediate teleports

	// Tracking state
	private boolean isHunllefMaging = false;
	private int previousAttackTick = 0;
	private int currentWeaponAttackSpeed = WEAPON_ATTACK_SPEED;

	@Override
	public void start()
	{
		eventBus.register(this);

		for (final NPC npc : client.getTopLevelWorldView().npcs())
		{
			onNpcSpawned(new NpcSpawned(npc));
		}

		// Determine if corrupted based on Hunllef NPC ID (more reliable than region)
		if (hunllef != null) {
			isCorrupted = hunllef.getId() == NpcID.CORRUPTED_HUNLLEF ||
				hunllef.getId() == NpcID.CORRUPTED_HUNLLEF_9036 ||
				hunllef.getId() == NpcID.CORRUPTED_HUNLLEF_9037 ||
				hunllef.getId() == NpcID.CORRUPTED_HUNLLEF_9038;
		} else {
			// Fallback to region check
			int[] regions = client.getTopLevelWorldView().getMapRegions();
			isCorrupted = regions != null && regions.length > 0 && regions[0] == 7512;
		}

		// Reset previous run data and start new tracking
		statsTracker.reset();
		statsTracker.startTracking(isCorrupted);
		inBossFight = true;
		isHunllefMaging = false;
		previousAttackTick = client.getTickCount(); // Give 4 ticks leeway at start
		currentWeaponAttackSpeed = WEAPON_ATTACK_SPEED;
		log.info("Started tracking new {} Gauntlet run", isCorrupted ? "Corrupted" : "Normal");

		overlayManager.add(timerOverlay);
		overlayManager.add(bossOverlay);
		timerOverlay.setHunllefStart();
	}

	@Override
	public void stop()
	{
		// If fight is still active, player teleported out or left early
		if (inBossFight && statsTracker.getCurrentRun() != null)
		{
			// Only save if fight lasted long enough (not an immediate disconnect)
			if (statsTracker.getCurrentRun().getTotalTicks() >= MIN_TICKS_FOR_VALID_RUN)
			{
				statsTracker.finishRun(false, "TELEPORT");
				historyManager.addRun(statsTracker.getCurrentRun());
			}
		}

		eventBus.unregister(this);
		overlayManager.remove(timerOverlay);
		overlayManager.remove(bossOverlay);
		timerOverlay.reset();
		tornadoes.clear();
		hunllef = null;
		inBossFight = false;
		isHunllefMaging = false;
		previousAttackTick = 0;
		currentWeaponAttackSpeed = WEAPON_ATTACK_SPEED;
		// Don't reset stats immediately - let the overlay display them in the lobby
		// Stats will be reset when starting a new fight
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				stop();
				break;
		}
	}

	@Subscribe
	public void onActorDeath(final ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			// Player died - finish run with failure
			inBossFight = false;
			if (statsTracker.getCurrentRun() != null &&
				statsTracker.getCurrentRun().getTotalTicks() >= MIN_TICKS_FOR_VALID_RUN)
			{
				statsTracker.finishRun(false, "DEATH");
				historyManager.addRun(statsTracker.getCurrentRun());
			}
			timerOverlay.onPlayerDeath();
		}
		else if (event.getActor() == hunllef)
		{
			// Boss died - finish run with success
			log.info("Hunllef died! Finishing successful run.");
			inBossFight = false;
			
			// Debug: Send chat message
			if (statsTracker.getCurrentRun() != null)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"[Gauntlet] Run finished! Ticks: " + statsTracker.getCurrentRun().getTotalTicks() + 
					" Min required: " + MIN_TICKS_FOR_VALID_RUN, null);
			}
			else
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"[Gauntlet] ERROR: getCurrentRun() is null!", null);
			}
			
			if (statsTracker.getCurrentRun() != null &&
				statsTracker.getCurrentRun().getTotalTicks() >= MIN_TICKS_FOR_VALID_RUN)
			{
				statsTracker.finishRun(true, "SUCCESS");
				log.info("Saving successful run with {} ticks", statsTracker.getCurrentRun().getTotalTicks());
				historyManager.addRun(statsTracker.getCurrentRun());
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"[Gauntlet] Run saved to history!", null);
			}
			else
			{
				log.warn("Run not saved - currentRun: {}, ticks: {}", 
					statsTracker.getCurrentRun() != null, 
					statsTracker.getCurrentRun() != null ? statsTracker.getCurrentRun().getTotalTicks() : 0);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"[Gauntlet] Run NOT saved - too short or null", null);
			}
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		if (!inBossFight)
		{
			return;
		}

		statsTracker.onTick();

		// Check for tornado hits
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		for (NPC tornado : tornadoes)
		{
			if (playerLocation.equals(tornado.getWorldLocation()))
			{
				statsTracker.onTornadoHit();
			}
		}

		// Check for floor tile damage
		var scene = client.getWorldView(client.getLocalPlayer().getLocalLocation().getWorldView()).getScene();
		var tiles = scene.getTiles();
		int tileX = playerLocation.getX() - scene.getBaseX();
		int tileY = playerLocation.getY() - scene.getBaseY();
		var currentTile = tiles[playerLocation.getPlane()][tileX][tileY];
		if (currentTile != null && currentTile.getGroundObject() != null && 
			currentTile.getGroundObject().getId() == DAMAGE_TILE_ID)
		{
			statsTracker.onFloorTileHit();
		}
	}

	@Subscribe
	public void onAnimationChanged(final AnimationChanged event)
	{
		if (!inBossFight)
		{
			return;
		}

		final int animationId = event.getActor().getAnimation();
		if (animationId < 0)
		{
			return;
		}

		// Player animation
		if (event.getActor() == client.getLocalPlayer())
		{
			if (Arrays.stream(PLAYER_ATTACK_ANIMATION_IDS).anyMatch(value -> value == animationId))
			{
				// Player attack
				statsTracker.onPlayerAttack();

				// Check wrong attack style
				if (!hasCorrectAttackStyle(animationId))
				{
					statsTracker.onWrongAttackStyle();
				}

				// Check wrong offensive prayer
				if (!hasCorrectOffensivePrayer(animationId))
				{
					statsTracker.onWrongOffensivePrayer();
				}

				// Update weapon speed
				currentWeaponAttackSpeed = animationId == PLAYER_SCEPTRE_ATTACK_ID ? 
					SCEPTRE_ATTACK_SPEED : WEAPON_ATTACK_SPEED;
				previousAttackTick = client.getTickCount();
			}
		}
		// Hunllef animation
		else if (event.getActor() == hunllef)
		{
			if (animationId == BOSS_ATTACK_ANIMATION_ID)
			{
				statsTracker.onHunllefAttack();

				// Check wrong defensive prayer
				if (!hasCorrectDefensivePrayer())
				{
					statsTracker.onWrongDefensivePrayer();
				}
			}
			else if (animationId == BOSS_STOMP_ANIMATION_ID)
			{
				statsTracker.onHunllefStomp();
			}
			else if (animationId == BOSS_SWITCH_TO_MAGE_ANIMATION_ID)
			{
				isHunllefMaging = true;
			}
			else if (animationId == BOSS_SWITCH_TO_RANGE_ANIMATION_ID)
			{
				isHunllefMaging = false;
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(final HitsplatApplied event)
	{
		if (!inBossFight)
		{
			return;
		}

		if (event.getActor() == client.getLocalPlayer())
		{
			// Player took damage
			int damage = event.getHitsplat().getAmount();
			statsTracker.onDamageTaken(damage);
		}
		else if (event.getActor() == hunllef)
		{
			// Damage dealt to boss
			int damage = event.getHitsplat().getAmount();
			statsTracker.onDamageDealt(damage);
		}
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event)
	{
		if (!inBossFight || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();

		// Detect teleport messages
		if (message.contains("You teleport") ||
			message.contains("You step through the portal") ||
			message.contains("You exit the Gauntlet"))
		{
			inBossFight = false;
			if (statsTracker.getCurrentRun() != null &&
				statsTracker.getCurrentRun().getTotalTicks() >= MIN_TICKS_FOR_VALID_RUN)
			{
				statsTracker.finishRun(false, "TELEPORT");
				historyManager.addRun(statsTracker.getCurrentRun());
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(final NpcSpawned event)
	{
		final NPC npc = event.getNpc();

		if (TORNADO_IDS.contains(npc.getId()))
		{
			tornadoes.add(npc);
		}
		else if (HUNLLEF_IDS.contains(npc.getId()))
		{
			hunllef = npc;
		}
	}

	@Subscribe
	public void onNpcDespawned(final NpcDespawned event)
	{
		final NPC npc = event.getNpc();

		if (TORNADO_IDS.contains(npc.getId()))
		{
			tornadoes.removeIf(t -> t == npc);
		}
		else if (HUNLLEF_IDS.contains(npc.getId()))
		{
			hunllef = null;
		}
	}

	private boolean hasCorrectOffensivePrayer(int animationId)
	{
		boolean isNoWeaponAttack = animationId == PLAYER_KICK_ATTACK_ID ||
			animationId == PLAYER_PUNCH_ATTACK_ID ||
			animationId == PLAYER_SCEPTRE_ATTACK_ID;

		// Melee attacks
		if (animationId == PLAYER_MELEE_ATTACK_ID || animationId == PLAYER_MELEE_ALT_ATTACK_ID || isNoWeaponAttack)
		{
			return client.isPrayerActive(Prayer.PIETY) ||
				client.isPrayerActive(Prayer.ULTIMATE_STRENGTH) ||
				client.isPrayerActive(Prayer.SUPERHUMAN_STRENGTH) ||
				client.isPrayerActive(Prayer.BURST_OF_STRENGTH);
		}

		// Mage attacks
		if (animationId == PLAYER_MAGE_ATTACK_ID)
		{
			return client.isPrayerActive(Prayer.AUGURY) ||
				client.isPrayerActive(Prayer.MYSTIC_MIGHT) ||
				client.isPrayerActive(Prayer.MYSTIC_LORE) ||
				client.isPrayerActive(Prayer.MYSTIC_WILL);
		}

		// Range attacks
		if (animationId == PLAYER_RANGE_ATTACK_ID)
		{
			return client.isPrayerActive(Prayer.RIGOUR) ||
				client.isPrayerActive(Prayer.EAGLE_EYE) ||
				client.isPrayerActive(Prayer.HAWK_EYE) ||
				client.isPrayerActive(Prayer.SHARP_EYE);
		}

		return false;
	}

	private boolean hasCorrectAttackStyle(int animationId)
	{
		if (hunllef == null)
		{
			return true;
		}

		switch (hunllef.getId())
		{
			// Protect from Melee - don't use melee
			case NpcID.CORRUPTED_HUNLLEF:
			case NpcID.CRYSTALLINE_HUNLLEF:
				return animationId != PLAYER_MELEE_ATTACK_ID &&
					animationId != PLAYER_KICK_ATTACK_ID &&
					animationId != PLAYER_PUNCH_ATTACK_ID &&
					animationId != PLAYER_MELEE_ALT_ATTACK_ID &&
					animationId != PLAYER_SCEPTRE_ATTACK_ID;
			// Protect from Missiles - don't use range
			case NpcID.CRYSTALLINE_HUNLLEF_9022:
			case NpcID.CORRUPTED_HUNLLEF_9036:
				return animationId != PLAYER_RANGE_ATTACK_ID;
			// Protect from Magic - don't use mage
			case NpcID.CRYSTALLINE_HUNLLEF_9023:
			case NpcID.CORRUPTED_HUNLLEF_9037:
				return animationId != PLAYER_MAGE_ATTACK_ID;
		}

		return true;
	}

	private boolean hasCorrectDefensivePrayer()
	{
		return isHunllefMaging ? 
			client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC) : 
			client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
	}
}
