package org.matsim.dashboard;

import org.matsim.facilities.FacilityAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;

import java.util.List;

/**
 * Locations of facilities in the Gunma scenario.
 */
public class GunmaFacilitiesDashboard implements Dashboard {

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Facilities";
		header.description = "Locations of facilities in the Gunma scenario.";
		header.fullScreen = true;


		List<String> activities = List.of( "work", "other");

		for (String activity : activities) {

			layout.row(activity)
				.el(MapPlot.class, (viz, data) -> {
					viz.title = "Facility Locations for %s".formatted(activity);
					viz.height = 8.;
					viz.setShape(data.computeWithPlaceholder(FacilityAnalysis.class, "%s/facilities.shp", activity), "ID");

					BackgroundLayer poiBackgroundLayer = new BackgroundLayer(data.computeWithPlaceholder(FacilityAnalysis.class, "%s/facilities.shp", activity));
					poiBackgroundLayer.setOnTop(true);
					poiBackgroundLayer.setBorderWidth(5);
					poiBackgroundLayer.setBorderColor("purple");

					viz.addBackgroundLayer("poi", poiBackgroundLayer);
				});
			layout.tab(activity).add(activity);
		}


	}
}
