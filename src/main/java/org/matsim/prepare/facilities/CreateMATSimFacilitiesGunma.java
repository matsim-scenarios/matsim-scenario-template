package org.matsim.prepare.facilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.*;
import picocli.CommandLine;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.nio.file.Path;
import java.util.*;

@CommandLine.Command(
	name = "facilitiesGunma",
	description = "Creates MATSim facilities from shape-file and network"
)
public class CreateMATSimFacilitiesGunma implements MATSimAppCommand {


	private static final Logger log = LogManager.getLogger(CreateMATSimFacilitiesGunma.class);
	@CommandLine.Option(names = "--network", required = true, description = "Path to car network")
	private Path network;

	@CommandLine.Option(names = "--output", required = true, description = "Path to output facility file")
	private Path output;

	@CommandLine.Option(names = "--telfacs", required = true, description = "Path to coordinates from telephone book")
	private Path telFacsPath;


	public static void main(String[] args) {
		new CreateMATSimFacilitiesGunma().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		// Random Number Generator
		SplittableRandom rnd = new SplittableRandom();

		// Create Facilities & Factory
		ActivityFacilities facilities = FacilitiesUtils.createActivityFacilities();
		ActivityFacilitiesFactory f = facilities.getFactory();


		// Define Activity Options
		ActivityOption aoHome = f.createActivityOption("home");
		ActivityOption aoWork = f.createActivityOption("work");
		ActivityOption aoEdu = f.createActivityOption("edu");
		ActivityOption aoOther = f.createActivityOption("other");

		// Read Coordinate Table from Telephone Book
		Table table = Table.read().usingOptions(
			CsvReadOptions.builder(telFacsPath.toFile())
				.build()
		);

		DoubleColumn xCol = table.doubleColumn("x");
		DoubleColumn yCol = table.doubleColumn("y");


		// Loop through each coordinate pair, and create a facility that has the option for all activity types!
		for (int i = 0; i < table.rowCount(); i++) {
			double x = xCol.get(i);
			double y = yCol.get(i);

			Id<ActivityFacility> id = CreateMATSimFacilities.generateId(facilities, rnd);

			// todo: consider adding link id?
			ActivityFacility facility = f.createActivityFacility(id, CoordUtils.round(new Coord(x, y)));
			facility.addActivityOption(aoHome);
			facility.addActivityOption(aoWork);
			facility.addActivityOption(aoEdu);
			facility.addActivityOption(aoOther);
			facilities.addActivityFacility(facility);
		}

		log.info("Created {} facilities, writing to {}", facilities.getFacilities().size(), output);

		FacilitiesWriter writer = new FacilitiesWriter(facilities);
		writer.write(output.toString());

		return 0;
	}



}
