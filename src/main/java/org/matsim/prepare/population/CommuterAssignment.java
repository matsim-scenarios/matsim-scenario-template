package org.matsim.prepare.population;

import it.unimi.dsi.fastutil.longs.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.application.options.CsvOptions;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Helper class for commuter assignment.
 */
public class CommuterAssignment {

	private static final Logger log = LogManager.getLogger(CommuterAssignment.class);

	private final Map<Long, SimpleFeature> zones;

	/**
	 * Outgoing commuter from zones to zone.
	 * Zone ID -> (Zone ID -> number of commuters)
	 */
	private final Long2ObjectMap<Long2DoubleMap> commuter;

	/**
	 * Maps home district to probabilities of commuting to other districts.
	 */
//	private final Int2ObjectMap<Int2DoubleMap> berlinCommuter;

	private final CsvOptions csv = new CsvOptions(CSVFormat.Predefined.Default);

	//todo: have a scaled the original values
	private final double sample;


	public CommuterAssignment(Long2ObjectMap<SimpleFeature> zones, Path commuterPath, double sample, Set<Long> filterZones) {
		this.sample = sample;

		// outgoing commuters
		this.commuter = new Long2ObjectOpenHashMap<>();
		this.zones = zones;

		// read commuters
		try (CSVParser parser = csv.createParser(commuterPath)) {
			for (CSVRecord row : parser) {
				long from;
				long to;
				try {
					from = Long.parseLong(row.get("from"));
					to = Long.parseLong(row.get("to"));
				} catch (NumberFormatException e) {
					continue;
				}

				if (filterZones != null) {

					if (!filterZones.contains(from) || !filterZones.contains(to)) {

						continue;

					}
				}

				String n = row.get("n");
				commuter.computeIfAbsent(from, k -> Long2DoubleMaps.synchronize(new Long2DoubleOpenHashMap()))
					.mergeDouble(to, Integer.parseInt(n), Double::sum);

			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

//		this.berlinCommuter = new Int2ObjectOpenHashMap<>();
//
//		try (CSVParser parser = csv.createParser(berlinCommuterPath)) {
//
//			for (CSVRecord row : parser) {
//				int home = Integer.parseInt(row.get("home"));
//				int work = Integer.parseInt(row.get("work"));
//
//				berlinCommuter.computeIfAbsent(home, k -> new Int2DoubleOpenHashMap())
//					.put(work, Double.parseDouble(row.get("n")));
//			}
//
//			// Normalize probabilities
//			for (Int2ObjectMap.Entry<Int2DoubleMap> kv : berlinCommuter.int2ObjectEntrySet()) {
//				Int2DoubleMap m = kv.getValue();
//				double sum = m.values().doubleStream().sum();
//				m.replaceAll((k, v) -> v / sum);
//			}
//
//		} catch (IOException e) {
//			throw new UncheckedIOException(e);
//		}

	}

		/**
	 * Select and return a commute target.
	 *
	 * @param f   sampler producing target locations
	 * @param zoneId origin zone
	 */
	public ActivityFacility selectTarget(SplittableRandom rnd, long zoneId, double dist, Point refPoint, Sampler f) {

		// Commute in same zone
		// From origin zone, grab possible target zones with available commuters
		Long2DoubleMap comms = commuter.get(zoneId);
		if (!commuter.containsKey(zoneId) || comms.isEmpty())
			return null;

		// Create list of available destination zones
		LongList entries;
		synchronized (comms) {
			entries = new LongArrayList(comms.keySet());
		}

		// Loop through destination zones
		while (!entries.isEmpty()) {
			long key = entries.removeLong(rnd.nextInt(entries.size()));

			SimpleFeature ft = zones.get(key);

			// TODO: should maybe not be allowed
			if (ft == null)
				continue;

			Geometry zone = (Geometry) ft.getDefaultGeometry();

			// Zones too far away don't need to be considered
			if (zone.distance(refPoint) > dist * 1.2)
				continue;

			ActivityFacility res = f.sample(zone);

			if (res != null) {
				synchronized (comms) {
					double old = comms.get(key);

					// Check if other thread reduced the counter while computing
					// result needs to be thrown away
					if (old <= 0) {
						comms.remove(key);
						continue;
					}

					// subtract available commuters
					double newValue = old - (1 / sample);
					comms.put(key, newValue);

					if (newValue <= 0)
						comms.remove(key);
				}

				return res;
			}
		}


		return null;
	}

	/**
	 * Sample locations from specific zone.
	 */
	interface Sampler {

		ActivityFacility sample(Geometry zone);

	}

}
