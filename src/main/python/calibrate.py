#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import pandas as pd
import geopandas as gpd

from matsim.calibration import create_calibration, ASCCalibrator, utils, analysis

#%%

if os.path.exists("mid.csv"):
    srv = pd.read_csv("mid.csv")
    sim = pd.read_csv("sim.csv")

    _, adj = analysis.calc_adjusted_mode_share(sim, srv)

    print(srv.groupby("mode").sum())

    print("Adjusted")
    print(adj.groupby("mode").sum())

    adj.to_csv("mid_adj.csv", index=False)

#%%

modes = ["walk", "car", "ride", "pt", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -0.141210,
    "pt": 0.0781477780346438,
    "car": 0.871977390743304,
    "ride": -2.22873502992
}

# FIXME: Adjust
target = {
    "walk": 0.1,
    "bike": 0.1,
    "pt": 0.1,
    "car": 0.1,
    "ride": 0.1
}

region = gpd.read_file("../scenarios/dilutionArea.shp").set_crs("EPSG:25832")
homes = pd.read_csv("template-v1.0-homes.csv", dtype={"person": "str"})


def filter_persons(persons):
    persons = pd.merge(persons, homes, how="inner", left_on="person", right_on="person")
    persons = gpd.GeoDataFrame(persons, geometry=gpd.points_from_xy(persons.home_x, persons.home_y))

    df = gpd.sjoin(persons.set_crs("EPSG:25832"), city, how="inner", op="intersects")

    print("Filtered %s persons" % len(df))

    return df


def filter_modes(df):
    df = df[df.main_mode != "freight"]
    df.loc[df.main_mode.str.startswith("pt_"), "main_mode"] = "pt"

    return df


# FIXME: Adjust paths and config

study, obj = create_calibration(
    "calib",
    ASCCalibrator(modes, initial, target, lr=utils.linear_scheduler(start=0.3, interval=15)),
    "matsim-template-1.0.jar",
    "../input/v1.0/[name]-v1.0.config.xml",
    args="--10pct",
    jvm_args="-Xmx55G -Xms55G -XX:+AlwaysPreTouch -XX:+UseParallelGC",
    transform_persons=filter_persons,
    transform_trips=filter_modes,
    chain_runs=utils.default_chain_scheduler, debug=False
)

#%%

study.optimize(obj, 10)
