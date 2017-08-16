package de.tum.bgu.msm.modules.personTripAssignment;

import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.MitoPerson;
import de.tum.bgu.msm.data.MitoTrip;

public class WeightAdjustingTripDistribution extends SimpleTripDistribution{

    WeightAdjustingTripDistribution() {

    }

    @Override
    public double getWeight(MitoHousehold household, MitoPerson person, MitoTrip trip) {
        double weight = super.getWeight(household, person, trip);
        int divisor = 1;
         divisor += household.getTripsByPurpose().get(trip.getTripPurpose()).stream().filter(
                 t -> t.getPerson() != null && t.getPerson().equals(person)).count();
        return weight / divisor;
    }
}