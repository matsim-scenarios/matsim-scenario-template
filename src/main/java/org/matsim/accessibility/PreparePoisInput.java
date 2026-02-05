package org.matsim.accessibility;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;
import org.matsim.facilities.FacilitiesWriter;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.File;
import java.util.Map;

import static org.matsim.core.scenario.ScenarioUtils.createScenario;

public class PreparePoisInput {

	public static void main(String[] args) {
		File osmPoiCsv = new File("../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.0/input/osm_buffer5km/poi_2450.csv");
		File poisXml = new File("../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.0/input/osm_buffer5km/pois.xml");


		// create scenario
		Config config = ConfigUtils.createConfig();
		Scenario scenario = createScenario(config);

		ActivityFacilitiesFactory aff = scenario.getActivityFacilities().getFactory();

		// 1) ADD SHINKANSEN STATIONS
		ActivityOption shinkansenAO = aff.createActivityOption("shinkansen");
		ActivityFacility takasaki = aff.createActivityFacility(Id.create("Takasaki", ActivityFacility.class), new Coord(35878.2,46039.3));
		takasaki.addActivityOption(shinkansenAO);
		scenario.getActivityFacilities().addActivityFacility(takasaki);

		ActivityFacility jomoKogen = aff.createActivityFacility(Id.create("Jōmō-Kōgen", ActivityFacility.class), new Coord(77005.12, 42678.01));
		jomoKogen.addActivityOption(shinkansenAO);
		scenario.getActivityFacilities().addActivityFacility(jomoKogen);

		ActivityFacility annakaHaruna = aff.createActivityFacility(Id.create("Annaka-Haruna", ActivityFacility.class), new Coord(40283.47, 31359.07));
		annakaHaruna.addActivityOption(shinkansenAO);
		scenario.getActivityFacilities().addActivityFacility(annakaHaruna);

		// 2) Add Single Point in middle of Gunma


		ActivityFacility middleFacility = aff.createActivityFacility(Id.create("middle_facility", ActivityFacility.class), new Coord(56183, 44853));
		middleFacility.addActivityOption(aff.createActivityOption("middle"));
		scenario.getActivityFacilities().addActivityFacility(middleFacility);



		// 3) ADD OSM POIs

		CsvReadOptions options = CsvReadOptions.builder(osmPoiCsv)
			.separator(',')          // e.g. for European CSVs
			.header(true)             // default: true
			.missingValueIndicator("") // optional
			.build();

		Table table = Table.read().csv(options);

		Map<String, ActivityOption> aoMap = Map.of("supermarket", aff.createActivityOption("supermarket"),
			"public_bath", aff.createActivityOption("public_bath"),
			"hospital", aff.createActivityOption("hospital"));


//		ActivityOption ao = aff.createActivityOption("supermarket");
		for (int i = 0; i < table.rowCount(); i++) {
			double x = table.doubleColumn("x").get(i);
			double y = table.doubleColumn("y").get(i);
			String type = table.stringColumn("type").getString(i);
			Id<ActivityFacility> id = Id.create(type + "-" + i, ActivityFacility.class);

			ActivityFacility fac = aff.createActivityFacility(id, new Coord(x, y));
			fac.addActivityOption(aoMap.get(type));
			scenario.getActivityFacilities().addActivityFacility(fac);
		}

		new FacilitiesWriter(scenario.getActivityFacilities()).write(poisXml.toString());

	}

	private static Coordinate transformCoordinate(CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS, Coordinate sourceCoordinate) throws TransformException, FactoryException {

		// Create transform
		boolean lenient = true;
		MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, lenient);

		// Create coordinate
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point sourcePoint = geometryFactory.createPoint(sourceCoordinate);

		// Transform
		Point targetPoint = (Point) org.geotools.geometry.jts.JTS.transform(sourcePoint, transform);

		return targetPoint.getCoordinate();
	}
}
