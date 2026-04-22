package org.matsim.run;

import org.matsim.analysis.ModeChoiceCoverageControllerListener;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.traffic.LinkStats;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.longDistanceFreightGER.tripExtraction.ExtractRelevantFreightTrips;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.checkers.VspScenarioCheckerImpl;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.util.List;

@CommandLine.Command(header = ":: Open Template Scenario ::", version = RunTemplateScenario.VERSION, mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({
		CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
		MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class, ExtractHomeCoordinates.class,
		CreateLandUseShp.class, ResolveGridCoordinates.class, FixSubtourModes.class, AdjustActivityToLinkDistances.class, XYToLinks.class
})
@MATSimApplication.Analysis({
		LinkStats.class, CheckPopulation.class
})
// FIXME: Rename scenario
public class RunTemplateScenario extends MATSimApplication {

	static final String VERSION = "1.0";

	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(25, 10, 1);


	public RunTemplateScenario(@Nullable Config config) {
		super(config);
	}

	// FIXME: update config path
	public RunTemplateScenario() {
		super(ConfigUtils.loadConfig(String.format("input/v%s/template-v%s-25pct.config.xml", VERSION, VERSION)));
	}

	public static void main(String[] args) {
		MATSimApplication.run(RunTemplateScenario.class, args);
	}

	@Nullable
	@Override
	protected Config prepareConfig(Config config) {

		// Add all activity types with time bins

		for (long ii = 600; ii <= 97200; ii += 600) {

			for (String act : List.of("home", "restaurant", "other", "visit", "errands", "accomp_other", "accomp_children",
					"educ_higher", "educ_secondary", "educ_primary", "educ_tertiary", "educ_kiga", "educ_other")) {
				config.scoring()
						.addActivityParams(new ScoringConfigGroup.ActivityParams(act + "_" + ii).setTypicalDuration(ii));
			}

			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("work_" + ii).setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("business_" + ii).setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("leisure_" + ii).setTypicalDuration(ii)
					.setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));

			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("shop_daily_" + ii).setTypicalDuration(ii)
					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("shop_other_" + ii).setTypicalDuration(ii)
					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
		}

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

		config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
		config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
		config.controller().setRunId(sample.adjustName(config.controller().getRunId()));

		config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
		config.qsim().setStorageCapFactor(sample.getSize() / 100.0);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort);
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

		// TODO: Config options

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		scenario.addScenarioChecker(new VspScenarioCheckerImpl());
	}

	@Override
	protected void prepareControler(Controler controller) {

		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControlerListenerBinding().to(ModeChoiceCoverageControllerListener.class);

			}
		});

//		controller.addOverridingModule(new AbstractModule() {
//			@Override
//			public void install() {
//				bind(CharyparNagelScoringFunctionFactory.class); // so it can be used as delegate
//				bind(ScoringFunctionFactory.class).to(SubpopulationDelegatingScoringFunctionFactory.class);
//
//				MapBinder<String, ScoringFunctionFactory> mapBinder = MapBinder.newMapBinder(this.binder(), String.class,
//					ScoringFunctionFactory.class);
//				mapBinder.addBinding("goodsTraffic").to(VehicleTypeBasedScoringFunctionFactory.class);
//				mapBinder.addBinding("commercialPersonTraffic").to(CharyparNagelScoringFunctionFactory.class);
//			}
//		});

		controller.addOverridingModule(new SimWrapperModule());

	}
}
