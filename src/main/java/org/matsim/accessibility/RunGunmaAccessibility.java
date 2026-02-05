package org.matsim.accessibility;

import org.apache.commons.io.FileUtils;
import org.matsim.application.ApplicationUtils;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityFromEvents;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.dashboard.AccessibilityDashboard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs offline accessibility analysis for Gunma Prefecture in Japan.
 */
public final class RunGunmaAccessibility {

	static final String coordinateSystem = "EPSG:2450";
	static final String dirToCopy = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.0/output/0000_base";

	static final String outputDir = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.0/output/2026-01-07/3/";
	static final List<String> relevantPois = List.of("supermarket","public_bath","hospital","shinkansen","middle");


	private RunGunmaAccessibility() {
		// prevent instantiation
	}


	public static void main(String[] args) throws IOException {

		FileUtils.copyDirectory(new File(dirToCopy), new File(outputDir));

		String mapCenterString = "139.0266,36.5211";

//		calculateAccessibility();


		// CREATE DASHBOARD
//		createDashboard(mapCenterString);
	}

	private static void calculateAccessibility() {
		AccessibilityConfigGroup accConfig = new AccessibilityConfigGroup();
		accConfig.setTileSize_m(500);

		accConfig.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromShapeFile);
		accConfig.setShapeFileCellBasedAccessibility("../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.0/input/shp/gunma_2450/gunma_2450.shp");

		// extents of gunma from qgis -- this really shouldn't be neccessary if we use shp file...
		accConfig.setBoundingBoxLeft(-8874.3893231801757793);
		accConfig.setBoundingBoxBottom(104516.0981667207088321);
		accConfig.setBoundingBoxRight(-1513.2262783532476078);
		accConfig.setBoundingBoxTop(117287.2301061319012661);


		accConfig.setTimeOfDay(8 * 3600.);

		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.car, Modes4Accessibility.teleportedWalk);
		for (Modes4Accessibility mode : Modes4Accessibility.values()) {
			accConfig.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

		// Accessibility Calculation
		String eventsFile = ApplicationUtils.matchInput("output_events.xml.gz", Path.of(outputDir)).toString();

		String networkFile = "../_playground/gunma/data/matsim_inputs/NetworkRed191211fromJOSM_noHighway_cle_speed0.66Adjusted.xml";

		String poisFile = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.0/input/osm_buffer5km/pois.xml";
		final Config config = ConfigUtils.createConfig();
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(outputDir);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.routing().setRoutingRandomness(0.);
		config.facilities().setInputFile(poisFile);

		config.global().setCoordinateSystem("EPSG:2450");

		config.network().setInputFile(networkFile);
		config.addModule(accConfig);

		MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config);



		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder(scenario, eventsFile, relevantPois);
		builder.build().run();
	}

	private static void createDashboard(String mapCenterString) {

		final Config config = ConfigUtils.createConfig();
		config.controller().setOutputDirectory(outputDir);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);


		//CONFIG
		config.controller().setLastIteration(0);
		config.controller().setWritePlansInterval(-1);
		config.controller().setWriteEventsInterval(-1);
		config.global().setCoordinateSystem(coordinateSystem);



		config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.none);

		//simwrapper
		SimWrapperConfigGroup group = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		group.setSampleSize(0.001);
		if (mapCenterString != null) {
			group.defaultParams().setMapCenter(mapCenterString);
		}
		group.setDefaultDashboards(SimWrapperConfigGroup.Mode.disabled);


		SimWrapper sw = SimWrapper.create(config)
			.addDashboard(new AccessibilityDashboard(coordinateSystem, relevantPois, List.of(Modes4Accessibility.car)));

		boolean append = false;
		try {
			sw.generate(Path.of(outputDir), append);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		sw.run(Path.of(outputDir));

	}
}
