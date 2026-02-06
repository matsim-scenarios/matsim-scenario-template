package org.matsim.facilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.GeoFileWriter;
import org.matsim.run.Activities;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@CommandLine.Command(
	name = "facility-analysis", description = "Prepare Facilities.",
	mixinStandardHelpOptions = true, showDefaultValues = true
)
@CommandSpec(requireRunDirectory = true,
	produces = {
		"%s/facilities.shp"
	}
)




public class FacilityAnalysis implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(FacilityAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(FacilityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(FacilityAnalysis.class);


	public static void main(String[] args) {
		new FacilityAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {


		// set up coordinate reference systems and transformations
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2450", true);
		CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326", true);
		MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);

		// set up shape file builder
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		// this needs to happen before setting "the_geom" for Point.class
		typeBuilder.setCRS(sourceCRS);
		typeBuilder.setName("poi");
		// this geometry field must be named "the_geom" for GeoTools to recognize it
		typeBuilder.add("the_geom", Point.class);
		typeBuilder.add("ID", String.class);
		typeBuilder.add(Activities.home.name(), boolean.class);
		typeBuilder.add(Activities.work.name(), boolean.class);
		typeBuilder.add(Activities.education.name(), boolean.class);
		typeBuilder.add(Activities.other.name(), boolean.class);
		typeBuilder.add("type_en", String.class);

		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());

		Collection<SimpleFeature> features = new ArrayList<>();


		// read facilities file

//		String facilitiesFile = "/Users/jakob/git/matsim-gunma/input/v1.2/gunma-v1.2-facilities.xml";
		String facilitiesFile = ApplicationUtils.matchInput("output_facilities.xml.gz", input.getRunDirectory()).toString();
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimFacilitiesReader(scenario).readFile(facilitiesFile);


		// loop through facilities
		GeometryFactory geometryFactory = new GeometryFactory();
		for (ActivityFacility facility : scenario.getActivityFacilities().getFacilities().values()) {

			Point point = geometryFactory.createPoint(new Coordinate(facility.getCoord().getX(), facility.getCoord().getY()));
			Point wgs84Point = (Point) JTS.transform(point, transform);

			boolean home = facility.getActivityOptions().containsKey(Activities.home.name());
			boolean work = facility.getActivityOptions().containsKey(Activities.work.name());
			boolean education = facility.getActivityOptions().containsKey(Activities.education.name());
			boolean other = facility.getActivityOptions().containsKey(Activities.other.name());

			String type = facility.getAttributes().getAttribute("type_en") != null ? facility.getAttributes().getAttribute("type_en").toString() : "undefined";

			features.add(builder.buildFeature(null, wgs84Point, facility.getId().toString(), home, work, education, other, type));

		}

		for (String activity : List.of("work", "education", "other")) {
			Path outputPath = input.getRunDirectory().resolve("analysis/facilities/" + activity + "/facilities.shp");
			List<SimpleFeature> filteredFeatures = features.stream().filter(x -> (boolean) x.getAttribute(activity)).toList();
			if (filteredFeatures.isEmpty()) {
				log.warn("No facilities found for activity {}. Skipping shapefile generation.", activity);
				continue;
			} else {
				Files.createDirectories(outputPath.getParent());
				GeoFileWriter.writeGeometries(filteredFeatures, outputPath.toString());
				log.info("Written {} facilities for activity {} to {}", filteredFeatures.size(), activity, outputPath);
			}
		}



		return 0;
	}


}
