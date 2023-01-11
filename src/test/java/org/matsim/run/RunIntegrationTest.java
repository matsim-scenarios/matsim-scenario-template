package org.matsim.run;

import org.junit.Test;
import org.matsim.application.MATSimApplication;

import static org.junit.Assert.*;

public class RunIntegrationTest {

	@Test
	public void runScenario() {

		assert MATSimApplication.execute(RunTemplateScenario.class,
				"--1pct",
				"--iteration", "2") == 0 : "Must return non error code";

	}
}
