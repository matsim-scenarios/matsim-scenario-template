
N := template
V := v1.0
CRS := EPSG:25832

JAR := matsim-$(N)-*.jar

export SUMO_HOME := $(abspath ../../sumo-1.8.0/)
osmosis := osmosis\bin\osmosis

.PHONY: prepare

$(JAR):
	mvn package

# Required files
input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany-210701.osm.pbf\
	  -o input/network.osm.pbf

input/network.osm: input/network.osm.pbf

	# FIXME: Adjust level of details and area

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street,service\
	 --bounding-box top=48.977 left=11.779 bottom=48.854 right=12.019\
	 --used-node --wb network-service.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street\
	 --bounding-box top=48.994 left=11.574 bottom=48.584 right=12.095\
	 --used-node --wb network-detailed.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
	 --bounding-box top=49.08 left=11.31 bottom=48.50 right=12.24\
	 --used-node --wb network-coarse.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,motorway_junction,trunk,trunk_link,primary,primary_link\
	 --used-node --wb network-germany.osm.pbf

	$(osmosis) --rb file=network-service.osm.pbf --rb file=network-germany.osm.pbf --rb file=network-coarse.osm.pbf --rb file=network-detailed.osm.pbf\
  	 --merge --merge --merge --wx $@

	rm network-service.osm.pbf
	rm network-detailed.osm.pbf
	rm network-coarse.osm.pbf
	rm network-germany.osm.pbf


input/sumo.net.xml: input/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --ramps.no-split\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml,$(SUMO_HOME)/data/typemap/osmNetconvertUrbanDe.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger,bicycle\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --proj "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
	 --osm-files $< -o=$@


input/$V/$N-$V-network.xml.gz: input/sumo.net.xml
	java -jar $(JAR) prepare network-from-sumo $<\
	 --output $@

	# FIXME: Adjust

	java -jar $(JAR) prepare network\
     --shp ../public-svn/matsim/scenarios/countries/de/$N/shp/prepare-network/av-and-drt-area.shp\
	 --network $@\
	 --output $@


input/$V/$N-$V-network-with-pt.xml.gz: input/$V/$N-$V-network.xml.gz
	# FIXME: Adjust GTFS

	java -Xmx20G -jar $(JAR) prepare transit-from-gtfs --network $<\
	 --name $N-$V --date "2021-08-18" --target-crs $(CRS) \
	 ../shared-svn/projects/$N/data/20210816_regio.zip\
	 ../shared-svn/projects/$N/data/20210816_train_short.zip\
	 ../shared-svn/projects/$N/data/20210816_train_long.zip\
	 --prefix regio_,short_,long_\
	 --shp ../shared-svn/projects/$N/data/pt-area/pt-area.shp\
	 --shp ../shared-svn/projects/$N/data/Bayern.zip\
	 --shp ../shared-svn/projects/$N/data/germany-area/germany-area.shp\

input/freight-trips.xml.gz: input/$V/$N-$V-network.xml.gz
	# FIXME: Adjust path

	java -jar $(JAR) prepare extract-freight-trips ../shared-svn/projects/german-wide-freight/v1.2/german-wide-freight-25pct.xml.gz\
	 --network ../shared-svn/projects/german-wide-freight/original-data/german-primary-road.network.xml.gz\
	 --input-crs EPSG:5677\
	 --target-crs $(CRS)\
	 --shp ../shared-svn/projects/$N.shp --shp-crs $(CRS)\
	 --output $@

input/$V/$N-$V-25pct.plans.xml.gz: input/freight-trips.xml.gz input/$V/$N-$V-network.xml.gz
	java -jar $(JAR) prepare trajectory-to-plans\
	 --name prepare --sample-size 0.25\
	 --population ../shared-svn/projects/$N/matsim-input-files/population.xml.gz\
	 --attributes  ../shared-svn/projects/$N/matsim-input-files/personAttributes.xml.gz

	java -jar $(JAR) prepare resolve-grid-coords\
	 scenarios/input/prepare-25pct.plans.xml.gz\
	 --input-crs $(CRS)\
	 --grid-resolution 300\
	 --landuse ../matsim-leipzig/scenarios/input/landuse/landuse.shp\
	 --output scenarios/input/prepare-25pct.plans.xml.gz

	java -jar $(JAR) prepare population scenarios/input/prepare-25pct.plans.xml.gz\
	 --output scenarios/input/prepare-25pct.plans.xml.gz

	java -jar $(JAR) prepare generate-short-distance-trips\
 	 --population scenarios/input/prepare-25pct.plans.xml.gz\
 	 --input-crs $(CRS)\
 	 --shp ../shared-svn/projects/$N/matsim-input-files/$N.shp --shp-crs $(CRS)\
 	 --num-trips 15216

	java -jar $(JAR) prepare xy-to-links --network scenarios/input/$N-$V-network.xml.gz --input scenarios/input/prepare-25pct.plans-with-trips.xml.gz --output $@

	java -jar $(JAR) prepare fix-subtour-modes --input $@ --output $@

	java -jar $(JAR) prepare merge-populations $@ $< --output $@

	java -jar $(JAR) prepare extract-home-coordinates $@ --csv scenarios/input/$N-$V-homes.csv

	java -jar $(JAR) prepare downsample-population $@\
    	 --sample-size 0.25\
    	 --samples 0.1 0.01\


check: input/$V/$N-$V-25pct.plans.xml.gz
	java -jar $(JAR) analysis check-population $<\
 	 --input-crs $(CRS)\
 	 --shp ../shared-svn/projects/$N/matsim-input-files/$N.shp --shp-crs $(CRS)

# Aggregated target
prepare: input/$V/$N-$V-25pct.plans.xml.gz input/$V/$N-$V-network-with-pt.xml.gz
	echo "Done"