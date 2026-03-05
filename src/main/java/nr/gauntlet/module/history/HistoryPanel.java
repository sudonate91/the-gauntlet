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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Plugin panel to display Gauntlet run history with charts and statistics
 */
@Slf4j
public class HistoryPanel extends PluginPanel
{
	private final RunHistoryManager historyManager;
	private JPanel runsContainer;
	private JLabel statsLabel;
	private JLabel personalBestLabel;

	@Inject
	public HistoryPanel(RunHistoryManager historyManager)
	{
		super(false);
		this.historyManager = historyManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		init();
		refresh();
	}

	private void init()
	{
		// Header
		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel titleLabel = new JLabel("Gauntlet Run History");
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(16f));
		titleLabel.setAlignmentX(CENTER_ALIGNMENT);
		headerPanel.add(titleLabel);

		headerPanel.add(Box.createVerticalStrut(10));

		// Stats summary
		statsLabel = new JLabel();
		statsLabel.setForeground(Color.LIGHT_GRAY);
		statsLabel.setAlignmentX(CENTER_ALIGNMENT);
		headerPanel.add(statsLabel);

		headerPanel.add(Box.createVerticalStrut(5));

		// Personal best
		personalBestLabel = new JLabel();
		personalBestLabel.setForeground(new Color(76, 175, 80)); // Green
		personalBestLabel.setAlignmentX(CENTER_ALIGNMENT);
		headerPanel.add(personalBestLabel);

		add(headerPanel, BorderLayout.NORTH);

		// Main content panel
		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Runs section
		JPanel runsPanel = new JPanel(new BorderLayout());
		runsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		runsPanel.setBorder(new EmptyBorder(0, 10, 0, 10));

		JLabel runsTitle = new JLabel("Recent Runs");
		runsTitle.setForeground(Color.WHITE);
		runsTitle.setFont(runsTitle.getFont().deriveFont(12f));
		runsTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
		runsPanel.add(runsTitle, BorderLayout.NORTH);

		// Container for run cards
		runsContainer = new JPanel();
		runsContainer.setLayout(new BoxLayout(runsContainer, BoxLayout.Y_AXIS));
		runsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(runsContainer);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(null);
		runsPanel.add(scrollPane, BorderLayout.CENTER);

		contentPanel.add(runsPanel);
		contentPanel.add(Box.createVerticalStrut(10));

		// Button panel - vertical layout for better space usage
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
		buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttonPanel.setBorder(new EmptyBorder(0, 10, 10, 10));

		// Primary action - View Full Report
		JButton viewReportButton = new JButton("View Full Report (HTML)");
		viewReportButton.setToolTipText("Opens interactive charts in your browser");
		viewReportButton.setFont(viewReportButton.getFont().deriveFont(14f).deriveFont(java.awt.Font.BOLD));
		viewReportButton.setBackground(new Color(33, 150, 243));
		viewReportButton.setForeground(Color.WHITE);
		viewReportButton.setAlignmentX(CENTER_ALIGNMENT);
		viewReportButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		viewReportButton.addActionListener(e -> exportHTML());
		buttonPanel.add(viewReportButton);

		buttonPanel.add(Box.createVerticalStrut(8));

		// Secondary actions in a row
		JPanel secondaryPanel = new JPanel(new GridLayout(1, 2, 5, 0));
		secondaryPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		secondaryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JButton exportCSVButton = new JButton("Export CSV");
		exportCSVButton.setToolTipText("Export raw data for analysis");
		exportCSVButton.addActionListener(e -> exportCSV());
		secondaryPanel.add(exportCSVButton);

		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(e -> refresh());
		secondaryPanel.add(refreshButton);

		buttonPanel.add(secondaryPanel);
		buttonPanel.add(Box.createVerticalStrut(8));

		// Clear button (danger action)
		JButton clearButton = new JButton("Clear All History");
		clearButton.setToolTipText("Permanently delete all run data");
		clearButton.setForeground(new Color(244, 67, 54)); // Red text
		clearButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		clearButton.addActionListener(e ->
		{
			int result = JOptionPane.showConfirmDialog(
				this,
				"Are you sure you want to delete all run history?\nThis cannot be undone!",
				"Clear History",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			if (result == JOptionPane.YES_OPTION)
			{
				historyManager.clearHistory();
				refresh();
			}
		});
		buttonPanel.add(clearButton);

		contentPanel.add(buttonPanel);

		add(contentPanel, BorderLayout.CENTER);
	}

	private void exportHTML()
	{
		File file = historyManager.exportToHTML();
		if (file != null)
		{
			try
			{
				Desktop.getDesktop().browse(file.toURI());
				JOptionPane.showMessageDialog(this,
					"Exported to: " + file.getAbsolutePath() + "\n\nOpening in browser...",
					"Export Successful",
					JOptionPane.INFORMATION_MESSAGE);
			}
			catch (Exception e)
			{
				log.error("Failed to open HTML file", e);
				JOptionPane.showMessageDialog(this,
					"Exported to: " + file.getAbsolutePath() + "\n\nCould not open automatically.",
					"Export Successful",
					JOptionPane.INFORMATION_MESSAGE);
			}
		}
		else
		{
			JOptionPane.showMessageDialog(this,
				"Failed to export HTML file.",
				"Export Failed",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private void exportCSV()
	{
		File file = historyManager.exportToCSV();
		if (file != null)
		{
			try
			{
				Desktop.getDesktop().open(file.getParentFile());
				JOptionPane.showMessageDialog(this,
					"Exported to: " + file.getAbsolutePath(),
					"Export Successful",
					JOptionPane.INFORMATION_MESSAGE);
			}
			catch (Exception e)
			{
				log.error("Failed to open directory", e);
				JOptionPane.showMessageDialog(this,
					"Exported to: " + file.getAbsolutePath(),
					"Export Successful",
					JOptionPane.INFORMATION_MESSAGE);
			}
		}
		else
		{
			JOptionPane.showMessageDialog(this,
				"Failed to export CSV file.",
				"Export Failed",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	public void refresh()
	{
		List<RunStats> history = historyManager.getHistory();

		// Update stats label
		int totalRuns = history.size();
		long successRuns = history.stream().filter(r -> "SUCCESS".equals(r.getOutcomeDisplay())).count();
		long deathRuns = history.stream().filter(r -> "DEATH".equals(r.getOutcomeDisplay())).count();
		long teleportRuns = history.stream().filter(r -> "TELEPORT".equals(r.getOutcomeDisplay())).count();
		statsLabel.setText(String.format("<html><center>Total: %d | Successes: %d | Deaths: %d | Teleports: %d</center></html>",
			totalRuns, successRuns, deathRuns, teleportRuns));

		// Find personal best (successful corrupted runs)
		RunStats personalBest = history.stream()
			.filter(r -> "SUCCESS".equals(r.getOutcomeDisplay()) && r.isCorrupted())
			.min((a, b) -> Integer.compare(a.getTotalTicks(), b.getTotalTicks()))
			.orElse(null);

		if (personalBest != null)
		{
			double minutes = personalBest.getTotalTicks() * 0.6 / 60.0;
			personalBestLabel.setText(String.format("🏆 Personal Best (CG): %d ticks (%.1f min) | %.1f%% efficiency",
				personalBest.getTotalTicks(), minutes, personalBest.getEfficiency()));
		}
		else
		{
			personalBestLabel.setText("🏆 No successful corrupted runs yet");
		}

		// Update card view - show most recent runs first
		runsContainer.removeAll();
		SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd");
		
		for (RunStats run : history)
		{
			JPanel card = createRunCard(run, dateFormat);
			runsContainer.add(card);
			runsContainer.add(Box.createVerticalStrut(4));
		}
		
		runsContainer.revalidate();
		runsContainer.repaint();
	}

	private JPanel createRunCard(RunStats run, SimpleDateFormat dateFormat)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		
		String outcome = run.getOutcomeDisplay();
		Color outcomeColor = outcome.equals("SUCCESS") ? new Color(76, 175, 80) :
											outcome.equals("TELEPORT") ? new Color(255, 193, 7) :
											new Color(244, 67, 54);
		
		Date runDate = new Date(run.getDate());
		SimpleDateFormat timeFormat = new SimpleDateFormat("MMM dd HH:mm");
		
		// Row 1: Date
		card.add(createRow("Date", timeFormat.format(runDate), Color.LIGHT_GRAY, Color.white));
		
		// Row 2: Ticks/Eff
		String ticksEff = String.format("%d ticks  •  %.0f%%", run.getTotalTicks(), run.getEfficiency());
		card.add(createRow("Ticks/Eff", ticksEff, Color.LIGHT_GRAY, Color.white));
		
		// Row 3: Type
		String type = run.isCorrupted() ? "Corrupted" : "Normal";
		Color typeColor = run.isCorrupted() ? new Color(220, 118, 51) : Color.GRAY;
		card.add(createRow("Type", type, Color.LIGHT_GRAY, typeColor));
		
		// Row 4: Outcome
		card.add(createRow("Outcome", outcome, Color.LIGHT_GRAY, outcomeColor));
		
		return card;
	}
	
	private JPanel createRow(String label, String value, Color labelColor, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		
		JLabel labelComponent = new JLabel(label);
		labelComponent.setForeground(labelColor);
		labelComponent.setFont(labelComponent.getFont().deriveFont(12f));
		
		JLabel valueComponent = new JLabel(value);
		valueComponent.setForeground(valueColor);
		valueComponent.setFont(valueComponent.getFont().deriveFont(12f).deriveFont(java.awt.Font.BOLD));
		valueComponent.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
		
		row.add(labelComponent, BorderLayout.WEST);
		row.add(valueComponent, BorderLayout.EAST);
		
		return row;
	}
}
