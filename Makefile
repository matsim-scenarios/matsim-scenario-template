# The following was modified manually quite heavily.  Unfortunately, it cannot be tested.  So it may no longer be consistent.  Sorry.  But without
# regression tests we do not know how to do better.  kai, gregorL, may'25


N := template
V := v1.0
CRS := EPSG:25832

MEMORY ?= 20G
JAR := matsim-$(N)-*.jar

ifndef SUMO_HOME
	export SUMO_HOME := $(abspath ../../sumo-1.15.0/)
endif

osmosis := osmosis/bin/osmosis

# Scenario creation tool
sc := java -Xmx$(MEMORY) -jar $(JAR)

.PHONY: prepare

$(JAR):
	mvn package

# download the germany file (maybe use newer version):
input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany-210701.osm.pbf\
	  -o input/network.osm.pbf

# extract items from the germany file at three levels of detail and merge them ($< will use the input file, which is input/network.osm.pbf)
input/network.osm: input/network.osm.pbf

	# FIXME: Adjust level of details and area

# extract detailed network (see param highway) from OSM.  The "poly" file is essentially a shp-file.  What people seem to have done is taken the
# polygon from the shp file, and manually changed it to the poly format.  Ask Simon Meinhardt.
	$(osmosis) --rb file=$<\
	 --tf accept-ways bicycle=yes highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street\
	 --bounding-polygon file="../shared-svn/projects/$N/data/area.poly"\
	 --used-node --wb input/network-detailed.osm.pbf

# extract the "intermediate" network:
		$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
	 --bounding-box top=51.92 left=11.45 bottom=50.83 right=13.36\
	 --used-node --wb input/network-coarse.osm.pbf

	#	retrieve germany wide network (see param highway) from OSM
	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,motorway_junction,trunk,trunk_link,primary,primary_link\
	 --used-node --wb input/network-germany.osm.pbf

#	put the 3 above networks together and remove railway
	$(osmosis) --rb file=input/network-germany.osm.pbf --rb file=input/network-coarse.osm.pbf --rb file=input/network-detailed.osm.pbf\
  	 --merge --merge\
  	 --tag-transform file=input/remove-railway.xml\
  	 --wx $@

	rm input/network-detailed.osm.pbf
	rm input/network-coarse.osm.pbf
	rm input/network-germany.osm.pbf


input/sumo.net.xml: input/network.osm

#	create sumo network from osm network
	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --ramps.no-split\
#	roadTypes are taken either from the general file "osmNetconvert.typ.xml" or from the german one "osmNetconvertUrbanDe.typ.xml"
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml,$(SUMO_HOME)/data/typemap/osmNetconvertUrbanDe.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger,bicycle\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --proj "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
	 --osm-files $< -o=$@


# transform sumo network to matsim network and clean it afterwards
# free-speed-factor is applied to all links with speed <= 50km/h.  Standard is 0.9; there is some tendency to set it to smaller values since that pushes more traffic on the motorways.
# Lausitz has 0.75.  Cleans the network for the given modes (car, bike).  Does not hurt to clean for modes that are, in the end, not routed on the network.  (?)
input/$V/$N-$V-network.xml.gz: input/sumo.net.xml
	$(sc) prepare network-from-sumo $< --output $@
	$(sc) prepare clean-network $@ --output $@ --modes car --modes bike
#	$(sc) prepare network --network $< --output $@
# add last line and corresponding class for example to add hbefa link classed and freight modes; look at matsim-lausitz (which will do the hbefa stuff
# and add freight modes).

#add pt to network from german wide gtfs, but only for area of shp file
input/$V/$N-$V-network-with-pt.xml.gz: input/$V/$N-$V-network.xml.gz
	$(sc) prepare transit-from-gtfs --network $<\
	 --output=input/$V\
	 --name $N-$V --date "2021-08-18" --target-crs $(CRS) \
	 ../shared-svn/projects/$N/data/20210816_regio.zip\
	 ../shared-svn/projects/$N/data/20210816_train_short.zip\
	 ../shared-svn/projects/$N/data/20210816_train_long.zip\
	 --prefix regio_,short_,long_\
	 --shp ../shared-svn/projects/$N/data/pt-area/pt-area.shp\
	 --shp ../shared-svn/projects/$N/data/Bayern.zip\
	 --shp ../shared-svn/projects/$N/data/germany-area/germany-area.shp\

input/plans-longHaulFreight.xml.gz: input/$V/$N-$V-network.xml.gz
	$(sc) prepare extract-freight-trips ../public-svn/matsim/scenarios/countries/de/german-wide-freight/v2/german_freight.100pct.plans.xml.gz\
	 --network ../public-svn/matsim/scenarios/countries/de/german-wide-freight/v2/germany-europe-network.xml.gz\
	 --input-crs EPSG:25832\
	 --target-crs $(CRS)\
	 --shp ../shared-svn/projects/$N/data/shp/$N.shp --shp-crs $(CRS)\
	 --cut-on-boundary\
	 # this will cut the trips at the borders of Germany; maybe remove this in the long run but would need some kind of motorway network outside
	 # Germany.  (Kai would very much prefer the latter.)
	 --LegMode "longDistanceFreight"\
	 --output $@

input/$V/prepare-25pct.plans.xml.gz:
	$(sc) prepare trajectory-to-plans\
	 --name prepare --sample-size 0.25 --output input/$V\
	 --population ../shared-svn/projects/$N/matsim-input-files/population.xml.gz\
	 --attributes  ../shared-svn/projects/$N/matsim-input-files/personAttributes.xml.gz

	$(sc) prepare resolve-grid-coords\
	 input/$V/prepare-25pct.plans.xml.gz\
	 --input-crs $(CRS)\
	 --grid-resolution 300\
	 --landuse ../matsim-leipzig/scenarios/input/landuse/landuse.shp\
	 --output $@

input/$V/$N-$V-25pct.plans-initial.xml.gz: input/freight-trips.xml.gz input/$V/$N-$V-network.xml.gz input/$V/prepare-25pct.plans.xml.gz
	$(sc) prepare generate-short-distance-trips\
 	 --population input/$V/prepare-25pct.plans.xml.gz\
 	 --input-crs $(CRS)\
	 --shp ../shared-svn/projects/$N/data/shp/$N.shp --shp-crs $(CRS)\
 	 --num-trips 111111 # FIXME

	$(sc) prepare adjust-activity-to-link-distances input/$V/prepare-25pct.plans-with-trips.xml.gz\
	 --shp ../shared-svn/projects/$N/data/shp/$N.shp --shp-crs $(CRS)\
     --scale 1.15\
     --input-crs $(CRS)\
     --network input/$V/$N-$V-network.xml.gz\
     --output input/$V/prepare-25pct.plans-adj.xml.gz

	$(sc) prepare xy-to-links --network input/$V/$N-$V-network.xml.gz --input input/$V/prepare-25pct.plans-adj.xml.gz --output $@

	$(sc) prepare fix-subtour-modes --input $@ --output $@

	$(sc) prepare merge-populations $@ $< --output $@

	$(sc) prepare extract-home-coordinates $@ --csv input/$V/$N-$V-homes.csv

	$(sc) prepare downsample-population $@\
    	 --sample-size 0.25\
    	 --samples 0.1 0.01\


check: input/$V/$N-$V-25pct.plans-initial.xml.gz
	$(sc) analysis check-population $<\
 	 --input-crs $(CRS)\
	 --shp ../shared-svn/projects/$N/data/shp/$N.shp --shp-crs $(CRS)

# Aggregated target
prepare: input/$V/$N-$V-25pct.plans-initial.xml.gz input/$V/$N-$V-network-with-pt.xml.gz
	echo "Done"
