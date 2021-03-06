package de.tum.bgu.msm;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.io.input.InputFeed;
import de.tum.bgu.msm.io.input.InputManager;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.util.Random;
import java.util.ResourceBundle;

/**
 * Implements the Microsimulation Transport Orchestrator (MITO)
 *
 * @author Rolf Moeckel
 * Created on Sep 18, 2016 in Munich, Germany
 * <p>
 * To run MITO, the following data need either to be passed in (using methods feedData) from another program or
 * need to be read from files and passed in (using method initializeStandAlone):
 * - zones
 * - autoTravelTimes
 * - transitTravelTimes
 * - timoHouseholds
 * - retailEmplByZone
 * - officeEmplByZone
 * - otherEmplByZone
 * - totalEmplByZone
 * - sizeOfZonesInAcre
 * All other data are read by function  manager.readAdditionalData();
 */

public class MitoModel {

    private static final Logger logger = Logger.getLogger(MitoModel.class);
    private static String scenarioName;

    private final InputManager manager;
    private final DataSet dataSet;

    private boolean initialised = false;

    public MitoModel(ResourceBundle resources) {
        this.dataSet = new DataSet();
        this.manager = new InputManager(dataSet);
        Resources.INSTANCE.setResources(resources);
        MitoUtil.initializeRandomNumber();
    }

    public void feedData(InputFeed feed) {
        manager.readFromFeed(feed);
        if(!initialised) {
            manager.readAdditionalData();
            initialised = true;
        }
    }

    public void initializeStandAlone() {
        // Read data if MITO is used as a stand-alone program and data are not fed from other program
        logger.info("  Reading input data for MITO");
        manager.readAsStandAlone();
        manager.readAdditionalData();
    }

    public void runModel() {
        long startTime = System.currentTimeMillis();
        logger.info("Started the Microsimulation Transport Orchestrator (MITO)");

        TravelDemandGenerator ttd = new TravelDemandGenerator(dataSet);
        ttd.generateTravelDemand();

        printOutline(startTime);
    }

    private void printOutline(long startTime) {
        String trips = MitoUtil.customFormat("  " + "###,###", dataSet.getTrips().size());
        logger.info("A total of " + trips.trim() + " microscopic trips were generated");
        logger.info("Completed the Microsimulation Transport Orchestrator (MITO)");
        float endTime = MitoUtil.rounder(((System.currentTimeMillis() - startTime) / 60000), 1);
        int hours = (int) (endTime / 60);
        int min = (int) (endTime - 60 * hours);
        logger.info("Runtime: " + hours + " hours and " + min + " minutes.");
    }

    public DataSet getTravelDemand() {
        return dataSet;
    }

    public void setBaseDirectory(String baseDirectory) {
        MitoUtil.setBaseDirectory(baseDirectory);
    }

    public static String getScenarioName() {
        return scenarioName;
    }

    public static void setScenarioName(String scenarioName) {
        scenarioName = scenarioName;
    }

    public void setRandomNumberGenerator(Random random) {
        MitoUtil.initializeRandomNumber(random);
    }
}
