package de.tum.bgu.msm.io.input;

import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;

import java.util.Map;

public class InputFeed {

    public final Map<Integer, Zone> zones;
    public final Map<String, TravelTimes> travelTimes;
    public final Map<Integer, MitoHousehold> households;

    public InputFeed(Map<Integer, Zone> zones, Map<String, TravelTimes> travelTimes, Map<Integer, MitoHousehold> households) {
        this.zones = zones;
        this.travelTimes = travelTimes;
        this.households = households;
    }
}
