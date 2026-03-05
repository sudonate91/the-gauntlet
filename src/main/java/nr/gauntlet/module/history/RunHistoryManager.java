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

package nr.gauntlet.module.history;

import nr.gauntlet.TheGauntletConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import static net.runelite.client.RuneLite.RUNELITE_DIR;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Manages persistence and retrieval of Gauntlet run history
 */
@Slf4j
@Singleton
public class RunHistoryManager
{
	private static final String CONFIG_GROUP = "thegauntlet";
	private static final String HISTORY_KEY = "runHistory";
	private static final int MAX_HISTORY_SIZE = 500;
	private static final File HISTORY_DIR = new File(RUNELITE_DIR, "gauntlet-history");
	private static final File MASTER_FILE = new File(HISTORY_DIR, "gauntlet-runs.json");
	private static final File CSV_FILE = new File(HISTORY_DIR, "gauntlet-runs.csv");
	private static final File HTML_FILE = new File(HISTORY_DIR, "gauntlet-runs.html");

	private final ConfigManager configManager;
	private final TheGauntletConfig config;
	private final Gson gson;

	@Getter
	private final List<RunStats> history;

	@Inject
	public RunHistoryManager(ConfigManager configManager, TheGauntletConfig config, Gson gson)
	{
		this.configManager = configManager;
		this.config = config;
		this.gson = gson;
		this.history = new ArrayList<>();

		// Create history directory if it doesn't exist
		log.info("History directory: {}", HISTORY_DIR.getAbsolutePath());
		if (!HISTORY_DIR.exists())
		{
			boolean created = HISTORY_DIR.mkdirs();
			log.info("Created history directory: {} (success: {})", HISTORY_DIR.getAbsolutePath(), created);
		}
		else
		{
			log.info("History directory already exists: {}", HISTORY_DIR.getAbsolutePath());
		}

		loadHistory();
	}

	/**
	 * Add a new run to history and persist it
	 */
	public void addRun(RunStats stats)
	{
		log.info("=== addRun called ===");
		log.info("Stats object: {}", stats);
		log.info("Config trackRunHistory: {}", config.trackRunHistory());
		
		if (stats == null)
		{
			log.error("CRITICAL: stats is null! Cannot save run.");
			return;
		}
		
		if (!config.trackRunHistory())
		{
			log.warn("Tracking disabled in config. Not saving run.");
			return;
		}

		log.info("Adding run to history. Ticks: {}, Outcome: {}", stats.getTotalTicks(), stats.getOutcomeDisplay());
		history.add(0, stats); // Add to front for most recent first
		log.info("Added run to history. Total runs now: {}", history.size());

		// Trim history if needed
		if (history.size() > MAX_HISTORY_SIZE)
		{
			log.info("Trimming history from {} to {}", history.size(), MAX_HISTORY_SIZE);
			history.subList(MAX_HISTORY_SIZE, history.size()).clear();
		}

		log.info("Calling saveHistory()...");
		saveHistory();
		log.info("Calling saveMasterFile()...");
		saveMasterFile();
		log.info("=== addRun complete ===");
	}

	/**
	 * Save all runs to master JSON file
	 */
	private void saveMasterFile()
	{
		log.info("Saving to master file: {}", MASTER_FILE.getAbsolutePath());
		log.info("File exists before write: {}", MASTER_FILE.exists());
		log.info("Parent directory exists: {}", MASTER_FILE.getParentFile().exists());
		
		try (FileWriter writer = new FileWriter(MASTER_FILE))
		{
			String json = gson.toJson(history);
			writer.write(json);
			writer.flush();
			log.info("Successfully saved {} runs to master file: {}", history.size(), MASTER_FILE.getAbsolutePath());
			log.info("File size after write: {} bytes", MASTER_FILE.length());
		}
		catch (IOException e)
		{
			log.error("Failed to save master file: {}", MASTER_FILE.getAbsolutePath(), e);
		}
	}

	/**
	 * Load history from config
	 */
	private void loadHistory()
	{
		history.clear();

		// Try to load from config first
		String json = configManager.getConfiguration(CONFIG_GROUP, HISTORY_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				Type listType = new TypeToken<ArrayList<RunStats>>()
				{
				}.getType();
				List<RunStats> loaded = gson.fromJson(json, listType);
				if (loaded != null)
				{
					history.addAll(loaded);
					log.debug("Loaded {} runs from config", history.size());
					return;
				}
			}
			catch (Exception e)
			{
				log.error("Failed to load history from config", e);
			}
		}

		// Fall back to loading from individual files
		loadFromFiles();
	}

	/**
	 * Load history from master JSON file
	 */
	private void loadFromFiles()
	{
		if (!MASTER_FILE.exists())
		{
			return;
		}

		try
		{
			String content = new String(Files.readAllBytes(MASTER_FILE.toPath()));
			Type listType = new TypeToken<ArrayList<RunStats>>()
			{
			}.getType();
			List<RunStats> loaded = gson.fromJson(content, listType);
			if (loaded != null)
			{
				history.addAll(loaded);
				log.debug("Loaded {} runs from master file", history.size());
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load master file", e);
		}
	}

	/**
	 * Persist history to config
	 */
	private void saveHistory()
	{
		try
		{
			String json = gson.toJson(history);
			configManager.setConfiguration(CONFIG_GROUP, HISTORY_KEY, json);
			log.info("Saved {} runs to config (group: {}, key: {})", history.size(), CONFIG_GROUP, HISTORY_KEY);
		}
		catch (Exception e)
		{
			log.error("Failed to save history to config", e);
		}
	}

	/**
	 * Clear all history
	 */
	public void clearHistory()
	{
		history.clear();
		configManager.unsetConfiguration(CONFIG_GROUP, HISTORY_KEY);

		// Delete master files
		if (MASTER_FILE.exists())
		{
			MASTER_FILE.delete();
		}
		if (CSV_FILE.exists())
		{
			CSV_FILE.delete();
		}
		if (HTML_FILE.exists())
		{
			HTML_FILE.delete();
		}
	}

	/**
	 * Export all runs to CSV file
	 */
	public File exportToCSV()
	{
		try (FileWriter writer = new FileWriter(CSV_FILE))
		{
			// Write header
			writer.write("Date,Total Ticks,Lost Ticks,Used Ticks,Used %,Player Attacks,Wrong Off Pray,Wrong Att Style,");
			writer.write("Hunllef Attacks,Wrong Def Pray,Hunllef Stomps,Tornado Hits,Floor Tile Hits,");
			writer.write("Damage Taken,DPS Taken,DPS Given,Type,Outcome\n");

			// Write data rows (chronological order - oldest to newest)
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			List<RunStats> chronological = new ArrayList<>(history);
			Collections.reverse(chronological);

			for (RunStats run : chronological)
			{
				writer.write(String.format("%s,%d,%d,%d,%.2f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%s,%s\n",
					sdf.format(new Date(run.getDate())),
					run.getTotalTicks(),
					run.getLostTicks(),
					run.getUsedTicks(),
					run.getEfficiency(),
					run.getPlayerAttacks(),
					run.getWrongOffensivePrayer(),
					run.getWrongAttackStyle(),
					run.getHunllefAttacks(),
					run.getWrongDefensivePrayer(),
					run.getHunllefStomps(),
					run.getTornadoHits(),
					run.getFloorTileHits(),
					run.getDamageTaken(),
					run.getDpsTaken(),
					run.getDpsGiven(),
					run.isCorrupted() ? "Corrupted" : "Normal",
					run.getOutcomeDisplay()
				));
			}

			log.info("Exported {} runs to CSV: {}", history.size(), CSV_FILE.getAbsolutePath());
			return CSV_FILE;
		}
		catch (IOException e)
		{
			log.error("Failed to export CSV", e);
			return null;
		}
	}

	/**
	 * Export all runs to HTML file (can be opened in browser and saved as PDF)
	 */
	public File exportToHTML()
	{
		try (FileWriter writer = new FileWriter(HTML_FILE))
		{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

			// Calculate summary stats
			int totalRuns = history.size();
			long successRuns = history.stream().filter(r -> "SUCCESS".equals(r.getOutcomeDisplay())).count();
			long deathRuns = history.stream().filter(r -> "DEATH".equals(r.getOutcomeDisplay())).count();
			long teleportRuns = history.stream().filter(r -> "TELEPORT".equals(r.getOutcomeDisplay())).count();
			long corrupted = history.stream().filter(RunStats::isCorrupted).count();

			double avgTicks = history.stream().mapToInt(RunStats::getTotalTicks).average().orElse(0);
			double avgEfficiency = history.stream().mapToDouble(RunStats::getEfficiency).average().orElse(0);
			double avgDpsGiven = history.stream().mapToDouble(RunStats::getDpsGiven).average().orElse(0);

			// Write HTML
			writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
			writer.write("<meta charset=\"UTF-8\">\n");
			writer.write("<title>Gauntlet Run History</title>\n");
			writer.write("<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n");
			writer.write("<style>\n");
			writer.write("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
			writer.write(".container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }\n");
			writer.write("h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
			writer.write("h2 { color: #555; margin-top: 30px; }\n");
			writer.write(".summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin: 20px 0; }\n");
			writer.write(".stat-box { background: #f9f9f9; padding: 15px; border-left: 4px solid #4CAF50; }\n");
			writer.write(".stat-label { font-size: 12px; color: #777; text-transform: uppercase; }\n");
			writer.write(".stat-value { font-size: 24px; font-weight: bold; color: #333; margin-top: 5px; }\n");
			writer.write("table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
			writer.write("th { background: #4CAF50; color: white; padding: 12px; text-align: left; font-weight: 600; }\n");
			writer.write("td { padding: 10px; border-bottom: 1px solid #ddd; }\n");
			writer.write("tr:hover { background: #f5f5f5; }\n");
			writer.write(".success { color: #4CAF50; font-weight: bold; }\n");
			writer.write(".failed { color: #f44336; font-weight: bold; }\n");
			writer.write(".teleport { color: #FF9800; font-weight: bold; }\n");
			writer.write(".corrupted { color: #9C27B0; }\n");
			writer.write(".charts-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 30px; margin: 30px 0; }\n");
			writer.write(".chart-container { background: #fff; padding: 20px; border: 1px solid #ddd; border-radius: 8px; }\n");
			writer.write(".chart-container h3 { margin-top: 0; color: #555; font-size: 16px; }\n");
			writer.write("canvas { max-height: 300px; }\n");
			writer.write("@media print { body { background: white; } .container { box-shadow: none; } }\n");
			writer.write("</style>\n</head>\n<body>\n");
			writer.write("<div class=\"container\">\n");

			// Header
			writer.write("<h1>Gauntlet Run History</h1>\n");
			writer.write(String.format("<p>Generated: %s</p>\n", sdf.format(new Date())));

			// Summary Stats
			writer.write("<h2>Summary Statistics</h2>\n");
			writer.write("<div class=\"summary\">\n");
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Total Runs</div><div class=\"stat-value\">%d</div></div>\n", totalRuns));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Success</div><div class=\"stat-value\" style=\"color: #4CAF50;\">%d</div></div>\n", successRuns));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Deaths</div><div class=\"stat-value\" style=\"color: #f44336;\">%d</div></div>\n", deathRuns));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Teleports</div><div class=\"stat-value\" style=\"color: #FF9800;\">%d</div></div>\n", teleportRuns));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Corrupted</div><div class=\"stat-value\" style=\"color: #9C27B0;\">%d</div></div>\n", corrupted));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Avg Ticks</div><div class=\"stat-value\">%.0f</div></div>\n", avgTicks));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Avg Efficiency</div><div class=\"stat-value\">%.1f%%</div></div>\n", avgEfficiency));
			writer.write(String.format("<div class=\"stat-box\"><div class=\"stat-label\">Avg DPS Given</div><div class=\"stat-value\">%.2f</div></div>\n", avgDpsGiven));
			writer.write("</div>\n");

			// Performance Charts
			writer.write("<h2>Performance Trends</h2>\n");
			writer.write("<div class=\"charts-grid\">\n");
			writer.write("<div class=\"chart-container\"><h3>Tick Efficiency (%)</h3><canvas id=\"chartEfficiency\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>Wrong Offensive Prayers</h3><canvas id=\"chartWrongOffPray\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>Wrong Attack Styles</h3><canvas id=\"chartWrongAttStyle\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>Wrong Defensive Prayers</h3><canvas id=\"chartWrongDefPray\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>Tornado Hits</h3><canvas id=\"chartTornadoHits\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>Floor Tile Hits</h3><canvas id=\"chartFloorHits\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>DPS Given</h3><canvas id=\"chartDpsGiven\"></canvas></div>\n");
			writer.write("<div class=\"chart-container\"><h3>DPS Taken</h3><canvas id=\"chartDpsTaken\"></canvas></div>\n");
			writer.write("</div>\n");

			// Detailed Table
			writer.write("<h2>All Runs</h2>\n");
			writer.write("<table>\n<thead>\n<tr>\n");
			writer.write("<th>Date</th><th>Ticks</th><th>Used %</th><th>Attacks</th><th>Off Pray</th><th>Att Style</th>");
			writer.write("<th>Def Pray</th><th>Stomps</th><th>Tornado</th><th>Floor</th><th>Damage</th>");
			writer.write("<th>DPS (T/G)</th><th>Type</th><th>Outcome</th>\n");
			writer.write("</tr>\n</thead>\n<tbody>\n");
			
			// Write rows (newest first)
			for (RunStats run : history)
			{
				String outcome = run.getOutcomeDisplay();
				String outcomeClass = outcome.equals("SUCCESS") ? "success" :
													outcome.equals("TELEPORT") ? "teleport" : "failed";
				String outcomeSymbol = outcome.equals("SUCCESS") ? "✓" :
													outcome.equals("TELEPORT") ? "◄" : "✗";

				writer.write("<tr>\n");
				writer.write(String.format("<td>%s</td>\n", sdf.format(new Date(run.getDate()))));
				writer.write(String.format("<td>%d</td>\n", run.getTotalTicks()));
				writer.write(String.format("<td>%.1f%%</td>\n", run.getEfficiency()));
				writer.write(String.format("<td>%d</td>\n", run.getPlayerAttacks()));
				writer.write(String.format("<td>%d</td>\n", run.getWrongOffensivePrayer()));
				writer.write(String.format("<td>%d</td>\n", run.getWrongAttackStyle()));
				writer.write(String.format("<td>%d</td>\n", run.getWrongDefensivePrayer()));
				writer.write(String.format("<td>%d</td>\n", run.getHunllefStomps()));
				writer.write(String.format("<td>%d</td>\n", run.getTornadoHits()));
				writer.write(String.format("<td>%d</td>\n", run.getFloorTileHits()));
				writer.write(String.format("<td>%d</td>\n", run.getDamageTaken()));
				writer.write(String.format("<td>%.2f / %.2f</td>\n", run.getDpsTaken(), run.getDpsGiven()));
				writer.write(String.format("<td class=\"%s\">%s</td>\n",
					run.isCorrupted() ? "corrupted" : "",
					run.isCorrupted() ? "CG" : "Normal"));
				writer.write(String.format("<td class=\"%s\">%s %s</td>\n",
					outcomeClass,
					outcomeSymbol,
					outcome));
				writer.write("</tr>\n");
			}

			writer.write("</tbody>\n</table>\n");
			
			// Add JavaScript for charts
			writer.write("<script>\n");
			
			// Prepare data (chronological order - oldest to newest)
			List<RunStats> chronological = new ArrayList<>(history);
			Collections.reverse(chronological);
			
			// Helper function for dynamic moving average
			writer.write("function movingAverage(data, window) {\n");
			writer.write("  const result = [];\n");
			writer.write("  for (let i = 0; i < data.length; i++) {\n");
			writer.write("    const start = Math.max(0, i - window + 1);\n");
			writer.write("    const subset = data.slice(start, i + 1);\n");
			writer.write("    const avg = subset.reduce((a, b) => a + b, 0) / subset.length;\n");
			writer.write("    result.push(avg);\n");
			writer.write("  }\n");
			writer.write("  return result;\n");
			writer.write("}\n\n");
			
			// Calculate dynamic window size (10% of total runs, min 5, max 20)
			int maWindow = Math.max(5, Math.min(20, chronological.size() / 10));
			writer.write(String.format("const maWindow = %d; // Dynamic: 10%% of %d runs\n", maWindow, chronological.size()));
			
			// Extract data arrays
			writer.write("const labels = [");
			for (int i = 0; i < chronological.size(); i++) {
				writer.write(String.format("%d%s", i + 1, i < chronological.size() - 1 ? "," : ""));
			}
			writer.write("];\n");
			
			String[] metrics = {"efficiency", "wrongOffensivePrayer", "wrongAttackStyle", 
				"wrongDefensivePrayer", "tornadoHits", "floorTileHits", "dpsGiven", "dpsTaken"};
			String[] varNames = {"efficiency", "wrongOffPray", "wrongAttStyle", "wrongDefPray", 
				"tornadoHits", "floorHits", "dpsGiven", "dpsTaken"};
			
			for (int m = 0; m < metrics.length; m++) {
				writer.write(String.format("const %sData = [", varNames[m]));
				for (int i = 0; i < chronological.size(); i++) {
					RunStats run = chronological.get(i);
					double value = getMetricValue(run, metrics[m]);
					writer.write(String.format("%.2f%s", value, i < chronological.size() - 1 ? "," : ""));
				}
				writer.write("];\n");
			}
			
			// Create charts
			String[] chartIds = {"chartEfficiency", "chartWrongOffPray", "chartWrongAttStyle", 
				"chartWrongDefPray", "chartTornadoHits", "chartFloorHits", "chartDpsGiven", "chartDpsTaken"};
			String[] colors = {"75, 192, 192", "255, 99, 132", "255, 159, 64", "153, 102, 255", 
				"255, 205, 86", "54, 162, 235", "76, 175, 80", "244, 67, 54"};
			
			for (int i = 0; i < chartIds.length; i++) {
				writer.write(String.format("new Chart(document.getElementById('%s'), {\n", chartIds[i]));
				writer.write("  type: 'line',\n");
				writer.write("  data: {\n");
				writer.write("    labels: labels,\n");
				writer.write("    datasets: [{\n");
				writer.write("      label: 'Actual',\n");
				writer.write(String.format("      data: %sData,\n", varNames[i]));
				writer.write(String.format("      borderColor: 'rgba(%s, 0.5)',\n", colors[i]));
				writer.write(String.format("      backgroundColor: 'rgba(%s, 0.1)',\n", colors[i]));
				writer.write("      borderWidth: 2,\n");
				writer.write("      pointRadius: 3,\n");
				writer.write("      fill: true\n");
				writer.write("    }, {\n");
				writer.write(String.format("      label: maWindow + '-Run MA',\n"));
				writer.write(String.format("      data: movingAverage(%sData, maWindow),\n", varNames[i]));
				writer.write(String.format("      borderColor: 'rgba(%s, 1)',\n", colors[i]));
				writer.write("      borderWidth: 3,\n");
				writer.write("      pointRadius: 0,\n");
				writer.write("      fill: false\n");
				writer.write("    }]\n");
				writer.write("  },\n");
				writer.write("  options: {\n");
				writer.write("    responsive: true,\n");
				writer.write("    maintainAspectRatio: true,\n");
				writer.write("    interaction: { mode: 'index', intersect: false },\n");
				writer.write("    plugins: {\n");
				writer.write("      legend: { display: true, position: 'top' },\n");
				writer.write("      tooltip: { enabled: true, mode: 'index', intersect: false },\n");
				writer.write("      decimation: { enabled: labels.length > 100, algorithm: 'lttb', samples: 100 }\n");
				writer.write("    },\n");
				writer.write("    scales: {\n");
				writer.write("      x: { display: true, title: { display: true, text: 'Run #' }, ticks: { maxTicksLimit: 10 } },\n");
				writer.write("      y: { beginAtZero: true, ticks: { precision: 0 } }\n");
				writer.write("    },\n");
				writer.write("    elements: { point: { radius: labels.length > 50 ? 2 : 3 }, line: { tension: 0.1 } }\n");
				writer.write("  }\n");
				writer.write("});\n\n");
			}
			
			writer.write("</script>\n");
			writer.write("</div>\n</body>\n</html>");

			log.info("Exported {} runs to HTML: {}", history.size(), HTML_FILE.getAbsolutePath());
			return HTML_FILE;
		}
		catch (IOException e)
		{
			log.error("Failed to export HTML", e);
			return null;
		}
	}

	/**
	 * Get statistics for a specific metric across all runs
	 */
	public List<Double> getMetricValues(String metric)
	{
		List<Double> values = new ArrayList<>();

		for (RunStats run : history)
		{
			Double value = getMetricValue(run, metric);
			if (value != null)
			{
				values.add(value);
			}
		}

		Collections.reverse(values); // Chronological order for charts
		return values;
	}

	private Double getMetricValue(RunStats run, String metric)
	{
		switch (metric)
		{
			case "totalTicks": return (double) run.getTotalTicks();
			case "lostTicks": return (double) run.getLostTicks();
			case "usedTicks": return (double) run.getUsedTicks();
			case "playerAttacks": return (double) run.getPlayerAttacks();
			case "wrongOffensivePrayer": return (double) run.getWrongOffensivePrayer();
			case "wrongAttackStyle": return (double) run.getWrongAttackStyle();
			case "hunllefAttacks": return (double) run.getHunllefAttacks();
			case "wrongDefensivePrayer": return (double) run.getWrongDefensivePrayer();
			case "hunllefStomps": return (double) run.getHunllefStomps();
			case "tornadoHits": return (double) run.getTornadoHits();
			case "floorTileHits": return (double) run.getFloorTileHits();
			case "damageTaken": return (double) run.getDamageTaken();
			case "dpsTaken": return run.getDpsTaken();
			case "dpsGiven": return run.getDpsGiven();
			case "efficiency": return run.getEfficiency();
			default: return null;
		}
	}
}
