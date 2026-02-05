JAR := matsim-gunma-*.jar
V := v1.2

p := input/$V
gunma := ../shared-svn/projects/matsim-gunma/data

MEMORY ?= 30G

# in my case, osmosis can be called directly. - jr
osmosis := osmosis

# Scenario creation tool
sc := java -Xmx$(MEMORY) -XX:+UseParallelGC -cp $(JAR) org.matsim.prepare.RunOpenGunmaCalibration

$(JAR):
	mvn package


config: $p/gunma-$V-config.xml
network: $p/gunma-$V-network.xml
facilities: $p/gunma-$V-facilities.xml
plans: $p/gunma-locations-$V-1pct.plans.xml.gz


### X) Prepare Zonal Shape file


$(gunma)/processed/jis_zones/jis_zones.shp: $(gunma)/raw/shp/nationwide_municip_2020/N03-20_200101.shp
	ogr2ogr \
	  -t_srs EPSG:2450 \
	  -dialect SQLITE \
	  -sql "SELECT N03_007, ST_Union(geometry) AS geometry \
	        FROM \"N03-20_200101\" \
	        WHERE N03_007 IS NOT NULL AND N03_007 != '' \
	        GROUP BY N03_007" \
	  $@ \
	  $<

### A) CONFIG
$p/gunma-$V-config.xml: $(gunma)/raw/matsim_inputs_lichen_luo/config_simulation.xml
	$(sc) prepare prepare-config\
    	 --input $<\
    	 --output $@


### B) NETWORK
# We reuse the network from Luo (20XX)
# TODO: network currently doesn't have road types retained. Consider redoing network generation, so we can make sure no activities put by highways
$p/gunma-$V-network.xml: $(gunma)/raw/matsim_inputs_lichen_luo/NetworkRed191211fromJOSM_noHighway_cle_speed0.66Adjusted.xml
	cp $< $@

### C) FACILITIES
$p/gunma-$V-facilities.xml: $p/gunma-$V-network.xml
	$(sc) prepare facilitiesGunma --network $< \
	 --telfacs $(gunma-shared)/data/processed/facility_locations_yellowpages.csv \
	 --output $@

### D) POPULATION
# 1) Merge Shapefiles for census data
$(gunma)/raw/shp/mesh250m/mesh250m.shp: $(gunma)/raw/shp/mesh250m/QDDSWQ5338/MESH05338.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5438/MESH05438.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5439/MESH05439.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5538/MESH05538.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5539/MESH05539.shp
	ogr2ogr $@ $(word 1,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 2,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 3,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 4,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 5,$^) -nln mesh250m
	ogr2ogr -t_srs EPSG:2450 $(gunma)/raw/shp/mesh250m_census/mesh250m-2450.shp $@ -nln mesh250m



ddd: $p/gunma-static-$V-100pct.plans.xml.gz
# 2) Generate static population plans
# TODO WHY DOESN'T THIS USE THE FACILITIES FILE???
$p/gunma-static-$V-100pct.plans.xml.gz: $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/raw/shp/mesh250m_census/mesh250m-2450.shp input/facilities.gpkg
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--facilities $(word 3,$^) --facilities-attr resident\
		--output $@

	$(sc) prepare lookup-jis-code --input $@ --output $@ --shp $(gunma)/processed/jis_zones/jis_zones.shp

# 3) Downsample to 25% and 1%
$p/gunma-static-$V-1pct.plans.xml.gz: $p/gunma-static-$V-100pct.plans.xml.gz
	$(sc) prepare downsample-population $< \
		--sample-size 1.0 \
		--samples 0.25 0.01

# 3) Assign daily plan to each MATSim Agent
# Here we match MATSim agents with travel survey respondents, based on their age and gender. The daily plan
# from the travel survey respondent is copied to the MATSim agent, but the locations are deleted.
# TODO: I don't understand why assign-reference-population is necessary - it seems to be doing the same thing as activity-sampling. It even calls upon that class... I'm gonna skip it for now until I understand better


$p/gunma-activities-$V-1pct.plans.xml.gz: $p/gunma-static-$V-1pct.plans.xml.gz $(gunma)/processed/travel_survey/person_attributes.csv $(gunma)/processed/travel_survey/activities.csv
	$(sc) prepare activity-sampling \
		--seed 1 \
 		--input $< \
 		--output $@ \
 		--persons $(word 2,$^)\
 		--activities $(word 3,$^)

	#$(sc) prepare assign-reference-population --population $@ --output $@\
#	 --persons src/main/python/table-persons.csv\
#  	 --activities src/main/python/table-activities.csv\
#  	 --shp $(germany)/../matsim-berlin/data/SrV/zones/zones.shp\
#  	 --shp-crs $(CRS)\
#	 --facilities $(word 2,$^)\
#	 --network $(word 3,$^)\



# 4) Location Choice
$p/gunma-locations-$V-1pct.plans.xml.gz: $p/gunma-activities-$V-1pct.plans.xml.gz $p/gunma-$V-facilities.xml $p/gunma-$V-network.xml $(gunma)/processed/jis_zones/jis_zones.shp $(gunma)/processed/work_od_matrix.csv
	$(sc) prepare init-location-choice \
	 --input $< \
	 --output $@ \
	 --facilities $(word 2,$^) \
	 --network $(word 3,$^) \
	 --shp $(word 4,$^) \
	 --commuter $(word 5,$^) \
	 --sample 0.01 \
	 --k 1

# 5) Eval Run
$p/gunma-experienced-$V-1pct.plans.xml.gz: $p/gunma-$V-config.xml $p/gunma-locations-$V-1pct.plans.xml.gz
	$(sc) run --1pct --config $< --population $(word 2,$^) --mode eval

	cp output/eval.output_experienced_plans.xml.gz $@


output2/dashboard-1.yaml:
	$(sc) prepare simwrapper output2/
#




plans_exp: $p/gunma-experienced-$V-1pct.plans.xml.gz

# SKIPPED STEPS - these are steps done in the Berlin Scenario that we do not do here:
# Commercial Traffic
# prepare merge-plans -->  "This file requires eval runs" ??? What does that mean?

# Chose Plan to match the traffic counts...
