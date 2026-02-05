package org.matsim.dashboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.dashboard.*;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
	name = "gunma-dashboard",
	description = "Run standalone SimWrapper post-processing for existing run output."
)

/*
  This class runs the SimWrapper dashboard generation for Gunma simulations, as stand-alone post-processing step.
 */
public final class GunmaSimwrapperRunner implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(GunmaSimwrapperRunner.class);
	@CommandLine.Parameters(arity = "1..*", description = "Path to run output directories for which the dashboards is to be generated.")
	private List<Path> inputPaths;

	private GunmaSimwrapperRunner() {
	}

	public static void main(String[] args) {
		new GunmaSimwrapperRunner().execute(args);
	}

	@Override
	public Integer call() {

		for (Path runDirectory : inputPaths) {
			log.info("Creating dashboards for {}", runDirectory);

			Path configPath = ApplicationUtils.matchInput("config.xml", runDirectory);
			Config config = ConfigUtils.loadConfig(configPath.toString());
			SimWrapper sw = SimWrapper.create(config);

			SimWrapperConfigGroup simwrapperCfg = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);


			GunmaTripDashboard trips = new GunmaTripDashboard("resources/mode_share_ref.csv", "resources/mode_share_per_dist_ref.csv", "resources/mode_users_ref.csv")
				.withDistanceDistribution("resources/mode_share_distance_distribution.csv")
				.setAnalysisArgs("--match-id", "^gunma.+", "--shp-filter", "none")
				.withChoiceEvaluation(false);

			sw.addDashboard(trips);

			try {
				//replace existing dashboards
				boolean append = false;
				sw.generate(runDirectory, append);
				sw.run(runDirectory);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return 0;
	}

}

