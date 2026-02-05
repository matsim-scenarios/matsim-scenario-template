package org.matsim.prepare.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.options.ShpOptions.Index;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
	name = "lookup-jis-code",
	description = "JIS code lookup using coordinates."
)
public class LookupJisZone implements MATSimAppCommand, PersonAlgorithm {

	private static final Logger log = LogManager.getLogger(LookupJisZone.class);

	@CommandLine.Option(names = "--input", required = true, description = "Input Population")
	private Path input;

	@CommandLine.Option(names = "--output", required = true, description = "Output Population")
	private Path output;

	@CommandLine.Mixin
	private ShpOptions shp;

	/** spatial index, built once */
	private Index jisIndex;

	public static void main(String[] args) {
		new LookupJisZone().execute(args);
	}

	@Override
	public Integer call() {

		if (!shp.isDefined()) {
			log.error("Shape file with JIS zones is required.");
			return 2;
		}

		// Build index once
		jisIndex = shp.createIndex(
			shp.getShapeCrs(),
			Attributes.JIS_ZONE_FIELD
		);

		Population population = PopulationUtils.readPopulation(input.toString());
		population.getPersons().values().forEach(this::run);
		PopulationUtils.writePopulation(population, output.toString());

		return 0;
	}

	@Override
	public void run(Person person) {
		Coord homeCoord = Attributes.getHomeCoord(person);

		String jisCode = jisIndex.query(homeCoord);

		if (jisCode == null) {
			log.warn("No JIS zone found for person {} at {}", person.getId(), homeCoord);
			return;
		}

		person.getAttributes().putAttribute(Attributes.ZONE, jisCode);
	}
}
