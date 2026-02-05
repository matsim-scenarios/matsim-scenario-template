package org.matsim.prepare.config;

import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.run.Activities;
import org.matsim.run.OpenGunmaScenario;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
	name = "prepare-config",
	description = "Creates MATSim config for Gunma Region"
)

public class PrepareConfig implements MATSimAppCommand {

	@CommandLine.Option(names = "--input", description = "Path to input config.", required = true)
	private Path input;

	@CommandLine.Option(names = "--output", description = "Path to output config.", required = true)
	private Path output;

	public static void main(String[] args) {
		new PrepareConfig().execute(args);
	}

	@Override
	public Integer call() {
		Config config = ConfigUtils.createConfig();

		// global settings
		config.global().setCoordinateSystem(OpenGunmaScenario.CRS);

		// input files
		config.network().setInputFile(output.getFileName().toString().replace("config", "network"));
		config.facilities().setInputFile(output.getFileName().toString().replace("config", "facilities"));


		// activity params
//		ScoringConfigGroup.ActivityParams homeParam = new ScoringConfigGroup.ActivityParams(Activities.);
//		config.scoring().addActivityParams(homeParam);
//
//		Config configOld = new ConfigReader(ConfigUtils.createConfig()).readFile(input.toString());
//		// read old config, and copy over the activity params.
//		ConfigUtils.loadConfig(input.toString());
//		for (ScoringConfigGroup.ActivityParams activityParam : configOld.scoring().getActivityParams()) {
//			config.scoring().addActivityParams(activityParam);
//
//		}

		// write config
		ConfigUtils.writeConfig(config, output.toString());
		return 0;
	}

}
