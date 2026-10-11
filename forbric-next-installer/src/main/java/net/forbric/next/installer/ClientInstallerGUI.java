package net.forbric.next.installer;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;

/**
 * Forbric-NEXT client installer — simplified Swing GUI.
 *
 * <p>This is a skeleton: the install button validates inputs and creates the versions folder
 * structure, but does not yet download the 730MB merged game base. When the merged base
 * pipeline is production-ready, this GUI will call into it.
 */
public class ClientInstallerGUI extends JFrame {

	private final JComboBox<String> versionBox = new JComboBox<>();
	private final JTextField mcDirField = new JTextField(30);
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	private final JLabel statusLabel = new JLabel(" ");
	private final JButton installBtn = new JButton("Install");
	private final JButton browseBtn = new JButton("Browse…");

	public ClientInstallerGUI() {
		super("Forbric-NEXT Installer");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout(8, 8));

		// Top: version picker
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("Minecraft version:"));
		for (String v : InstallerVersionCatalog.all().keySet()) {
			versionBox.addItem(v);
		}
		top.add(versionBox);

		// Middle: .minecraft path
		JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT));
		mid.add(new JLabel(".minecraft:"));
		Path detected = InstallPaths.findMinecraftDir();
		mcDirField.setText(detected != null ? detected.toString() : "");
		mid.add(mcDirField);
		mid.add(browseBtn);

		// Bottom: progress + install
		JPanel bot = new JPanel(new BorderLayout(4, 4));
		bot.add(progressBar, BorderLayout.NORTH);
		bot.add(statusLabel, BorderLayout.CENTER);
		JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnRow.add(installBtn);
		bot.add(btnRow, BorderLayout.SOUTH);

		add(top, BorderLayout.NORTH);
		add(mid, BorderLayout.CENTER);
		add(bot, BorderLayout.SOUTH);

		// Wire actions
		browseBtn.addActionListener(e -> chooseDir());
		installBtn.addActionListener(e -> doInstall());

		pack();
		setLocationRelativeTo(null);
	}

	private void chooseDir() {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			mcDirField.setText(fc.getSelectedFile().getAbsolutePath());
		}
	}

	private void doInstall() {
		String mcVersion = (String) versionBox.getSelectedItem();
		String mcDir = mcDirField.getText().trim();

		if (mcDir.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select your .minecraft directory first.");
			return;
		}

		InstallerVersionCatalog.CatalogEntry entry = InstallerVersionCatalog.get(mcVersion);
		if (entry != null && !entry.exists()) {
			int ok = JOptionPane.showConfirmDialog(this,
					mcVersion + " does not exist. Continue anyway?",
					"Warning", JOptionPane.YES_NO_OPTION);
			if (ok != JOptionPane.YES_OPTION) return;
		}

		installBtn.setEnabled(false);
		statusLabel.setText("Installing Forbric-NEXT for MC " + mcVersion + "…");
		progressBar.setValue(10);

		// Skeleton: just create the versions folder
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				try {
					Thread.sleep(300);
					progressBar.setValue(40);

					Path target = InstallPaths.versionFolder(Path.of(mcDir), mcVersion);
					java.nio.file.Files.createDirectories(target);
					progressBar.setValue(80);

					// Write a placeholder json — real version json comes later
					String json = "{\n  \"id\": \"" + mcVersion + "-forbric-next\",\n  \"type\": \"release\",\n  \"mainClass\": \"net.forbric.next.launcher.Main\",\n  \"inheritsFrom\": \"" + mcVersion + "\"\n}";
					java.nio.file.Files.writeString(target.resolve(mcVersion + "-forbric-next.json"), json);

					progressBar.setValue(100);
					statusLabel.setText("Done. Installed to " + target);
				} catch (Exception ex) {
					statusLabel.setText("Error: " + ex.getMessage());
				}
				return null;
			}

			@Override
			protected void done() {
				installBtn.setEnabled(true);
			}
		}.execute();
	}

	public static void main(String[] args) {
		int javaVer = InstallPaths.detectJavaVersion();
		if (javaVer < 17) {
			System.err.println("Forbric-NEXT installer requires Java 17+. Detected: " + javaVer);
			System.exit(1);
		}
		SwingUtilities.invokeLater(() -> new ClientInstallerGUI().setVisible(true));
	}
}
