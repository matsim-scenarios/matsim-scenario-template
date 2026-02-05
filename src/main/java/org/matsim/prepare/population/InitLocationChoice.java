package org.matsim.prepare.population;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.tongfei.progressbar.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.facilities.ActivityFacility;
import org.matsim.prepare.RunOpenGunmaCalibration;
import org.matsim.prepare.facilities.AttributedActivityFacility;
import org.matsim.run.OpenGunmaScenario;
import picocli.CommandLine;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


@CommandLine.Command(
	name = "init-location-choice",
	description = "Assign initial locations to agents"
)
@SuppressWarnings("unchecked")
public class InitLocationChoice implements MATSimAppCommand, PersonAlgorithm {

	/**
	 * Detour factor for routes > 3000m. Factor is based on data, but adjusted to better match distance distribution.
	 */
//	private static final double DETOUR_FACTOR = 1.25;

	/**
	 * Factor for short trips < 3000m. Factor was calculated based on data.
	 */
//	private static final double DETOUR_FACTOR_SHORT = 1.3;

	private static final Logger log = LogManager.getLogger(InitLocationChoice.class);

	@CommandLine.Option(names = "--input", description = "Path to input population.",defaultValue = "/Users/jakob/git/matsim-gunma/input/v1.2/gunma-activities-v1.2-1pct.plans.xml.gz")
	private Path input;

	@CommandLine.Option(names = "--output", description = "Path to output population", required = true, defaultValue = "/Users/jakob/git/matsim-gunma/input/v1.2/gunma-locations-v1.2-1pct.plans.xml.gz")
	private Path output;

	@CommandLine.Option(names = "--k", description = "Number of choices to generate", defaultValue = "5")
	private int k;

	@CommandLine.Option(names = "--commuter", description = "Path to commuter.csv", required = true, defaultValue = "../shared-svn/projects/matsim-gunma/data/processed/work_od_matrix.csv")
	private Path commuterPath;
//
//	@CommandLine.Option(names = "--berlin-commuter", description = "Home work commuter within Berlin", required = true)
//	private Path berlinCommuterPath;

	@CommandLine.Option(names = "--facilities", description = "Path to facilities file", required = true,defaultValue = "/Users/jakob/git/matsim-gunma/input/v1.2/gunma-v1.2-facilities.xml")
	private Path facilityPath;

	@CommandLine.Option(names = "--network", description = "Path to network file", required = true, defaultValue = "/Users/jakob/git/matsim-gunma/input/v1.2/gunma-v1.2-network.xml")
	private Path networkPath;

	@CommandLine.Option(names = "--sample", description = "Sample size of the population", defaultValue = "0.25")
	private double sample;

	@CommandLine.Option(names = "--seed", description = "Seed used to sample locations", defaultValue = "1")
	private long seed;

	@CommandLine.Mixin
	private ShpOptions shp;

	private FacilityIndex facilities;

	private Long2ObjectMap<SimpleFeature> zones;

	private CommuterAssignment commuter;

	private Network network;

	private AtomicLong total = new AtomicLong();

	private AtomicLong warning = new AtomicLong();

	private ProgressBar pb;

	public static void main(String[] args) {
		new InitLocationChoice().execute(args);
	}


	static Coord rndCoord(SplittableRandom rnd, double dist, Coord origin) {
		double angle = rnd.nextDouble() * Math.PI * 2;

		double x = Math.cos(angle) * dist;
		double y = Math.sin(angle) * dist;

		return new Coord(RunOpenGunmaCalibration.roundNumber(origin.getX() + x), RunOpenGunmaCalibration.roundNumber(origin.getY() + y));
	}

	@Override
	public Integer call() throws Exception {


		if (shp.getShapeFile() == null) {
			log.error("Shape file with commuter zones is required.");
			return 2;
		}

		// Read population for purpose of gathering zones present in population
		Population populationTmp = PopulationUtils.readPopulation(input.toString());
		Set<Long> filteredZones = populationTmp.getPersons().values().stream().map(p -> Long.parseLong((String) p.getAttributes().getAttribute(Attributes.ZONE))).collect(Collectors.toSet());



		// Read Shapefile with JIS zones & save in map (zone code -> feature)
		zones = new Long2ObjectOpenHashMap<>(shp.readFeatures().stream()
			.filter(ft -> filteredZones.contains(Long.parseLong((String) ft.getAttribute(Attributes.JIS_ZONE_FIELD))))
			.collect(Collectors.toMap(ft -> Long.parseLong((String) ft.getAttribute(Attributes.JIS_ZONE_FIELD)), ft -> ft)));


		log.info("Read {} zones", zones.size());


		// Read network and filter links for car mode
		Network completeNetwork = NetworkUtils.readNetwork(networkPath.toString());
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(completeNetwork);
		network = NetworkUtils.createNetwork();
		filter.filter(network, Set.of(TransportMode.car));

		// Read facilities
		facilities = new FacilityIndex(facilityPath.toString(), OpenGunmaScenario.CRS);


		log.info("Using input file: {}", input);

		List<Population> populations = new ArrayList<>();

		// Creates k populations, each w/ one plan (each with different seed)
		for (int i = 0; i < k; i++) {

			log.info("Generating plan {} with seed {}", i, seed);


			// read population & gather set of zones present in population
			Population population = PopulationUtils.readPopulation(input.toString());
//			Set<Long> filteredZones = population.getPersons().values().stream().map(p -> Long.parseLong((String) p.getAttributes().getAttribute(Attributes.ZONE))).collect(Collectors.toSet());


			// initialize commuter assignment: gathers OD matrix from file
			commuter = new CommuterAssignment(zones, commuterPath, sample,filteredZones);



			pb = new ProgressBar("Performing location choice " + i, population.getPersons().size());

			// runs "run" method in parallel
			ParallelPersonAlgorithmUtils.run(population, Runtime.getRuntime().availableProcessors() - 1, this);

			populations.add(population);

			log.info("Processed {} activities with {} warnings", total.get(), warning.get());

			total.set(0);
			warning.set(0);
			seed += i;
		}

		Population population = populations.get(0);

		// Merge all plans into the first population
		for (int i = 1; i < populations.size(); i++) {

			Population pop = populations.get(i);

			for (Person p : pop.getPersons().values()) {
				Person destPerson = population.getPersons().get(p.getId());
				if (destPerson == null) {
					log.warn("Person {} not present in all populations.", p.getId());
					continue;
				}

				destPerson.addPlan(p.getPlans().get(0));
			}
		}

		PopulationUtils.writePopulation(population, output.toString());

		return 0;
	}

	@Override
	public void run(Person person) {

		Coord homeCoord = Attributes.getHomeCoord(person);
		long jisZone = Long.parseLong((String) person.getAttributes().getAttribute(Attributes.ZONE));

		// Reference persons are not assigned locations
		if (person.getAttributes().getAttribute(Attributes.REF_MODES) != null) {
			pb.step();
			return;
		}

		// Activities that only occur on one place per person
		Map<String, ActivityFacility> fixedLocations = new HashMap<>();

		// shouldn't the person only have a single plan?
		int planNumber = 0;
		for (Plan plan : person.getPlans()) {
			List<Activity> acts = TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities);

			// keep track of the current coordinate
			Coord lastCoord = homeCoord;

			// Person specific rng, increment plan number for each plan
			SplittableRandom rnd = initRandomNumberGenerator(person, planNumber++);

			// loop thru activities in plan
			for (Activity act : acts) {

				// global counter for number of activities processed
				total.incrementAndGet();


				// if activity doesn't have link assigned to it (link id == "unassigned")
				if (Attributes.isLinkUnassigned(act.getLinkId())) {

					String type = act.getType();

					act.setLinkId(null);
					ActivityFacility location = null;

					// target leg distance in km
					double dist = (double) act.getAttributes().getAttribute("orig_dist");

					// Distance will be reduced to beeline distance
					// todo: we don't have to do this, since we are already using the beeline distance
//					double dist = beelineDist(origDist);

					// if we have already set a location for this person and  activity type, then we reuse it
					if (fixedLocations.containsKey(type)) {
						location = fixedLocations.get(type);
					}

					// Special handling for work activities: sample commute based on OD matrix
					if (location == null && type.equals("work")) {
						// sample work commute
						location = sampleCommute(rnd, dist, lastCoord, jisZone);
					}

					if (location == null && facilities.index.containsKey(type)) {
						// Needed for lambda
						final Coord refCoord = lastCoord;


						// Try to find a facility within the bounds
						// increase bounds if no facility is found
						for (Double b : DoubleList.of(1, 1.2, 1.5)) {
							List<AttributedActivityFacility> query = facilities.index.get(type).query(MGC.coord2Point(lastCoord).buffer(dist * (b + 0.2)).getEnvelopeInternal());
							// Distance should be within the bounds
							List<AttributedActivityFacility> res = query.stream().filter(f -> checkDistanceBound(dist, refCoord, f.getCoord(), b)).toList();

							// if locations were found, we choose one based on the weight.
							if (!res.isEmpty()) {
								location = FacilityIndex.sample(query, rnd);
//								location = query.get(FacilityIndex.sampleByWeight(query, AttributedActivityFacility::getOtherAttraction, rnd));
								break;
							}
						}
					}
					if (location == null) {
						// sample only coordinate if nothing else is possible
						// Activities without facility entry, or where no facility could be found
						Coord c = sampleLink(rnd, dist, lastCoord);
						act.setCoord(c);
						lastCoord = c;

						// An activity with type could not be put into correct facility.
						if (facilities.index.containsKey(type)) {
							warning.incrementAndGet();
						}

						continue;
					}

					if (type.equals("work") || type.startsWith("edu"))
						fixedLocations.put(type, location);

					act.setFacilityId(location.getId());
				}

				if (act.getCoord() != null)
					lastCoord = act.getCoord();
				else if (act.getFacilityId() != null)
					lastCoord = facilities.all.getFacilities().get(act.getFacilityId()).getCoord();

			}
		}

		pb.step();
	}

	/**
	 * Initializes random number generator with person specific seed.
	 */
	private SplittableRandom initRandomNumberGenerator(Person person, long planNumber) {
		BigInteger i = new BigInteger(person.getId().toString().getBytes());
		return new SplittableRandom(i.longValue() + seed * 1000 + planNumber * 10);
	}

	/**
	 * Sample work place by using commute and distance information.
	 * rnd = random number generator
	 * dist = target distance between previous activity and work place
	 * refCoord = coordinate of previous activity
	 * homeZone = JIS zone of the person's home
	 */
	private ActivityFacility sampleCommute(SplittableRandom rnd, double dist, Coord refCoord, long homeZone) {

		STRtree index = facilities.index.get("work");

		ActivityFacility workPlace = null;

		// Only larger distances can be commuters to other zones
		// TODO: dist refers to previous activity, so we cannot be sure that this is actually the home-work distance
		if (dist > 3000) {
			workPlace = commuter.selectTarget(rnd, homeZone, dist, MGC.coord2Point(refCoord), zone -> sampleZone(index, dist, refCoord, zone, rnd));
		}

		// Within Berlin, separate data for commute is used
//		if (workPlace == null && ars == 110000000000L && homeZone != null) {
//			workPlace = sampleBerlinWorkPlace(index, dist, refCoord, homeZone, rnd);
//		}

		if (workPlace == null) {
			// Try selecting within same zone
			// TODO: same as above regarding distance
			workPlace = sampleZone(index, dist, refCoord, (Geometry) zones.get(homeZone).getDefaultGeometry(), rnd);
		}

		return workPlace;
	}

	/**
	 * Sample a coordinate for which the associated link is not one of the ignored types.
	 */
	private Coord sampleLink(SplittableRandom rnd, double dist, Coord origin) {

		Coord coord = null;
		for (int i = 0; i < 500; i++) {
			coord = rndCoord(rnd, dist, origin);
			Link link = NetworkUtils.getNearestLink(network, coord);
			// TODO: again, our network currently does not contain this information /
//			if (!IGNORED_LINK_TYPES.contains(NetworkUtils.getType(link)))
			break;
		}

		return coord;
	}

	/**
	 * Samples randomly from the zone.
	 */
	private ActivityFacility sampleZone(STRtree index, double dist, Coord refCoord, Geometry zone, SplittableRandom rnd) {

		// gets all facilities within circle w/ radius = 1.2 x dist
		List<AttributedActivityFacility> query = index.query(MGC.coord2Point(refCoord).buffer(dist * 1.2).getEnvelopeInternal());

		// filters this list to contains only facilities within a band around the distance
		query = query.
			stream().
			filter(f -> checkDistanceBound(dist, refCoord, f.getCoord(), 1)).
			// todo: is this right?
			filter(f -> zone.contains(MGC.coord2Point(f.getCoord()))).
			collect(Collectors.toList());

//		return FacilityIndex.sampleByWeightWithRejection(query, f -> zone.contains(MGC.coord2Point(f.getCoord())), AttributedActivityFacility::getWorkAttraction, rnd);
		return FacilityIndex.sample(query, rnd);
	}

	/**
	 * Only samples randomly from the zone, ignoring the distance.
	 */
//	private ActivityFacility sampleBerlinWorkPlace(STRtree index, double dist, Coord refCoord, String homeZone, SplittableRandom rnd) {
//
//		List<AttributedActivityFacility> query = index.query(MGC.coord2Point(refCoord).buffer(dist * 1.2).getEnvelopeInternal());
//
//		query = query.stream()
//			.filter(f -> f.getZone() != null)
//			.filter(f -> checkDistanceBound(dist, refCoord, f.getCoord(), 1))
//			.collect(Collectors.toList());
//
//		if (query.isEmpty())
//			return null;
//
//		int idx = FacilityIndex.sampleByWeight(query,
//			f -> f.getWorkAttraction() * commuter.getZoneWeight(homeZone, f.getZone()), rnd);
//
//		return query.get(idx);
//	}

	/**
	 * General logic to filter coordinate within target distance.
	 */
	private boolean checkDistanceBound(double target, Coord refCoord, Coord other, double factor) {

		// Constant added to the bounds, needed for trips with low base distance
		double constant = (factor - 0.95) * 1000;

		// Percentage based bounds
		double lower = target * 0.8 * (2 - factor) - constant;
		double upper = target * 1.15 * factor + constant;

		double dist = CoordUtils.calcEuclideanDistance(refCoord, other);
		return dist >= lower && dist <= upper;
	}

}
