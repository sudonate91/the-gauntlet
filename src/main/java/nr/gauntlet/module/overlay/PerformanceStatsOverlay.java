/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2024, LlemonDuck
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

package nr.gauntlet.module.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import nr.gauntlet.TheGauntletConfig;
import nr.gauntlet.module.history.RunStats;
import nr.gauntlet.module.history.StatsTracker;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Overlay that displays performance statistics after a Gauntlet run
 */
@Singleton
public class PerformanceStatsOverlay extends OverlayPanel
{
	private static final int REGION_ID_GAUNTLET_LOBBY = 12127;

	private final Client client;
	private final TheGauntletConfig config;
	private final StatsTracker statsTracker;

	@Inject
	public PerformanceStatsOverlay(Client client, TheGauntletConfig config, StatsTracker statsTracker)
	{
		this.client = client;
		this.config = config;
		this.statsTracker = statsTracker;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RunStats stats = statsTracker.getCurrentRun();

		// Only show in lobby after a run that was properly finished
		if (!isInGauntletLobby() || stats == null || stats.getTotalTicks() <= 0 || stats.getOutcome() == null)
		{
			return null;
		}

		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Gauntlet Performance")
			.build());

		// Total ticks
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Total ticks")
			.right(Integer.toString(stats.getTotalTicks()))
			.build());

		// Lost ticks
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Lost ticks")
			.right(Integer.toString(stats.getLostTicks()))
			.build());

		// Efficiency
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Tick efficiency")
			.right(String.format("%.1f%%", stats.getEfficiency()))
			.build());

		// Player attacks
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Player attacks")
			.right(Integer.toString(stats.getPlayerAttacks()))
			.build());

		// Wrong offensive prayer
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Wrong off pray")
			.right(Integer.toString(stats.getWrongOffensivePrayer()))
			.build());

		// Wrong attack style
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Wrong att style")
			.right(Integer.toString(stats.getWrongAttackStyle()))
			.build());

		// Hunllef attacks
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Hunllef attacks")
			.right(Integer.toString(stats.getHunllefAttacks()))
			.build());

		// Wrong defensive prayer
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Wrong def pray")
			.right(Integer.toString(stats.getWrongDefensivePrayer()))
			.build());

		// Hunllef stomps
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Hunllef stomps")
			.right(Integer.toString(stats.getHunllefStomps()))
			.build());

		// Tornado hits
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Tornado hits")
			.right(Integer.toString(stats.getTornadoHits()))
			.build());

		// Floor tile hits
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Floor tile hits")
			.right(Integer.toString(stats.getFloorTileHits()))
			.build());

		// Damage taken
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Damage taken")
			.right(Integer.toString(stats.getDamageTaken()))
			.build());

		// DPS taken
		panelComponent.getChildren().add(LineComponent.builder()
			.left("DPS taken")
			.right(String.format("%.2f", stats.getDpsTaken()))
			.build());

		// DPS given
		panelComponent.getChildren().add(LineComponent.builder()
			.left("DPS given")
			.right(String.format("%.2f", stats.getDpsGiven()))
			.build());

		// Outcome
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Outcome")
			.right(stats.getOutcomeDisplay())
			.build());

		return super.render(graphics);
	}

	private boolean isInGauntletLobby()
	{
		var player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}

		int regionId = WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID();
		return regionId == REGION_ID_GAUNTLET_LOBBY;
	}
}
