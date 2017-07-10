package de.tum.bgu.msm;

import com.pb.common.datafile.TableDataSet;
import com.pb.common.matrix.Matrix;
import com.pb.common.util.ResourceUtil;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.MitoPerson;
import de.tum.bgu.msm.data.Zone;
import omx.OmxFile;
import omx.OmxMatrix;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Holds data for the Transport in Microsimulation Orchestrator (TIMO)
 * @author Rolf Moeckel
 * Created on Sep 18, 2016 in Munich, Germany
 *
 */

public class MitoData {

    private static final String PROPERTIES_ZONAL_DATA_FILE          = "zonal.data.file";
    private static final String PROPERTIES_AUTO_PEAK_SKIM           = "auto.peak.sov.skim";
    private static final String PROPERTIES_TRANSIT_PEAK_SKIM        = "transit.peak.time";
    private static final String PROPERTIES_HH_FILE_ASCII            = "household.file.ascii";
    private static final String PROPERTIES_PP_FILE_ASCII            = "person.file.ascii";
    private static final String PROPERTIES_JJ_FILE_ASCII            = "job.file.ascii";
    private static final String PROPERTIES_EMPLOYMENT_FILE          = "employment.forecast";
    private static final String PROPERTIES_SCHOOL_ENROLLMENT_FILE   = "school.enrollment.data";
    private static final String PROPERTIES_DISTANCE_RASTER_CELLS    = "distanceODmatrix";

    private static Logger logger = Logger.getLogger(MitoData.class);
    private ResourceBundle rb;
    private static String scenarioName;

    private TableDataSet htsHH;
    private TableDataSet htsTR;

    private Matrix autoTravelTimes;
    private Matrix transitTravelTimes;

    private String[] purposes;
    private HashMap<String, Integer> purposeIndices;
    private boolean removeTripsAtBorder;
    private TableDataSet reductionNearBorder;
    private Matrix distanceMatrix;

    private Map<Integer, Zone> zones;
    private Map<Integer, MitoHousehold> households;
    private Map<Integer, MitoPerson> persons;



    MitoData(ResourceBundle rb) {
        this.rb = rb;
    }


    void setScenarioName (String scenName) {
        scenarioName = scenName;
    }

    private static String getScenarioName() {
        return scenarioName;
    }

    public static String generateOutputFileName (String fileName) {
        if (MitoData.getScenarioName() != null) {
            File dir = new File("scenOutput/" + MitoData.getScenarioName() + "/tripGeneration");
            if(!dir.exists()){
                boolean directoryCreated = dir.mkdir();
                if (!directoryCreated) logger.warn("Could not create directory for trip gen output: " + dir.toString());
            }
            fileName = "scenOutput/" + MitoData.getScenarioName() + "/tripGeneration/" + fileName;
        }
        return fileName;
    }

    public Map<Integer,Zone> getZones() {
        return zones;
    }

    public String[] getPurposes() {
        return this.purposes;
    }

    public int getPurposeIndex (String purp) {
        return purposeIndices.get(purp);
    }

    public boolean shallWeRemoveTripsAtBorder() {
        return removeTripsAtBorder;
    }

    public void setAutoTravelTimes (Matrix autoTravelTimes) {
        this.autoTravelTimes = autoTravelTimes;
    }

    public float getAutoTravelTimes(int origin, int destination) {
        return autoTravelTimes.getValueAt(origin, destination);
    }

    public void setTransitTravelTimes (Matrix transitTravelTimes) {
        this.transitTravelTimes = transitTravelTimes;
    }

    public float getTransitTravelTimes(int origin, int destination) {
        return transitTravelTimes.getValueAt(origin, destination);
    }

    public float getDistances(int from, int to) {
        return this.distanceMatrix.getValueAt(from, to);
    }

    public void setDistances(Matrix matrix) {
        this.distanceMatrix = matrix;
    }

    public void setHouseholds(List<MitoHousehold> mitoHouseholds) {

        households.clear();
        for(MitoHousehold household: mitoHouseholds) {
            households.put(household.getHhId(), household);
            zones.get(household.getHomeZone()).addHousehold();
        }
    }

    public void setPersons(Map<Integer, MitoPerson> mitoPersons) {
        this.persons = mitoPersons;
    }

    public Map<Integer, MitoHousehold> getMitoHouseholds() {
        return households;
    }

    public int getNumberOfHouseholdsByZone (int zone) {
        return zones.get(zone).getNumberOfHouseholds();
    }


    void readZones() {

        zones = new HashMap<>();
        // read in zones from file
        String fileName = ResourceUtil.getProperty(rb, PROPERTIES_ZONAL_DATA_FILE);
        TableDataSet zonalData = MitoUtil.readCSVfile(fileName);
        for(int i = 0; i < zonalData.getRowCount(); i++) {
            Zone zone = new Zone(zonalData.getColumnAsInt("ZoneId")[i], zonalData.getValueAt(i, "ACRES"));
            zones.put(zone.getZoneId(), zone);
        }
        removeTripsAtBorder = ResourceUtil.getBooleanProperty(rb, "reduce.trips.at.outer.border", false);
        if (removeTripsAtBorder) {
            reductionNearBorder = MitoUtil.readCSVfile(rb.getString("reduction.near.outer.border"));
            for(int i = 0; i < reductionNearBorder.getRowCount(); i++) {
                float id = reductionNearBorder.getValueAt(i,"Zone");
                float damper = reductionNearBorder.getValueAt(i, "damper");
                if(zones.containsKey(id)) {
                    zones.get(id).setReductionAtBorderDamper(damper);
                } else {
                    logger.warn("Damper of " + damper + " refers to non-existing zone " + id + ". Ignoring it.");
                }
            }
        }
        defineRegions();
    }

    public void readSkims() {
        // Read highway and transit skims
        logger.info("  Reading skims");

        // Read highway hwySkim
        String hwyFileName = rb.getString(PROPERTIES_AUTO_PEAK_SKIM);
        OmxFile hSkim = new OmxFile(hwyFileName);
        hSkim.openReadOnly();
        OmxMatrix timeOmxSkimAutos = hSkim.getMatrix("HOVTime");
        autoTravelTimes = MitoUtil.convertOmxToMatrix(timeOmxSkimAutos);

        // Read transit hwySkim
        String transitFileName = rb.getString(PROPERTIES_TRANSIT_PEAK_SKIM);
        OmxFile tSkim = new OmxFile(transitFileName);
        tSkim.openReadOnly();
        OmxMatrix timeOmxSkimTransit = tSkim.getMatrix("CheapJrnyTime");
        transitTravelTimes = MitoUtil.convertOmxToMatrix(timeOmxSkimTransit);
    }

    public void readDistance() {

        //Read the skim matrix
        logger.info("   Starting to read OMX matrix");
        String omxFileName = ResourceUtil.getProperty(rb, PROPERTIES_DISTANCE_RASTER_CELLS);
        OmxFile travelTimeOmx = new OmxFile(omxFileName);
        travelTimeOmx.openReadOnly();
        distanceMatrix = MitoUtil.convertOmxToMatrix(travelTimeOmx.getMatrix("mat1"));
        for (int i = 1; i <= distanceMatrix.getRowCount(); i++) {
            for (int j = 1; j <= distanceMatrix.getColumnCount(); j++) {
                if (i == j) {
                    distanceMatrix.setValueAt(i, j, 50 / 1000);
                } else {
                    distanceMatrix.setValueAt(i, j, distanceMatrix.getValueAt(i, j) / 1000);
                }
            }
        }
        logger.info("   Read OMX matrix");
    }

    public void readHouseholdData() {
        logger.info("  Reading household micro data from ascii file");

        List<MitoHousehold> households = new ArrayList<>();

        String fileName = ResourceUtil.getProperty(rb, PROPERTIES_HH_FILE_ASCII);

        String recString = "";
        int recCount = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            recString = in.readLine();

            // read header
            String[] header = recString.split(",");
            int posId    = MitoUtil.findPositionInArray("id", header);
            int posDwell = MitoUtil.findPositionInArray("dwelling",header);
            int posTaz   = MitoUtil.findPositionInArray("zone",header);
            int posSize  = MitoUtil.findPositionInArray("hhSize",header);
            int posAutos = MitoUtil.findPositionInArray("autos",header);

            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int id         = Integer.parseInt(lineElements[posId]);
                int taz        = Integer.parseInt(lineElements[posTaz]);
                int hhSize     = Integer.parseInt(lineElements[posSize]);
                int autos      = Integer.parseInt(lineElements[posAutos]);
                MitoHousehold household = new MitoHousehold(id, hhSize, 0, 0, 0, 0,0, 0, 0, 0, autos, taz);
                households.add(household);
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop household file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("  Finished reading " + recCount + " households.");
        setHouseholds(households);
    }

    public void readPersonData() {
        logger.info("  Reading person micro data from ascii file");

        String fileName = ResourceUtil.getProperty(rb, PROPERTIES_PP_FILE_ASCII);

        String recString = "";
        int recCount = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            recString = in.readLine();

            // read header
            String[] header = recString.split(",");
            int posId = MitoUtil.findPositionInArray("id",header);
            int posHhId = MitoUtil.findPositionInArray("hhid",header);
            int posAge = MitoUtil.findPositionInArray("age", header);
            int posSex = MitoUtil.findPositionInArray("gender", header);
            int posOccupation = MitoUtil.findPositionInArray("occupation",header);
            int posWorkplace = MitoUtil.findPositionInArray("workplace",header);
            int posLicence = MitoUtil.findPositionInArray("driversLicense", header);
            int posIncome = MitoUtil.findPositionInArray("income",header);

            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int id = Integer.parseInt(lineElements[posId]);
                int hhid = Integer.parseInt(lineElements[posHhId]);
                MitoHousehold hh = households.get(hhid);
                int age = Integer.parseInt(lineElements[posAge]);
                if (age < 18) {
                    hh.setChildren(hh.getChildren() + 1);
                } else if (age >= 18 && age <= 25) {   // todo: Ana, is this the right definition of young adult?
                    hh.setYoungAdults(hh.getYoungAdults() + 1);
                } else if (age >= 65) {
                    hh.setRetirees(hh.getRetirees() + 1);
                }
                boolean student =  true;  // todo: How do we know who is a student? How are students defined?
                if (student) hh.setStudents(hh.getStudents() + 1);
                if (Integer.parseInt(lineElements[posSex]) == 2) {
                    hh.setFemales(hh.getFemales() + 1);
                }
                int occupation = Integer.parseInt(lineElements[posOccupation]);
                if (occupation == 1) {
                    hh.setNumberOfWorkers(hh.getNumberOfWorkers() + 1);
                }
                int workplace = Integer.parseInt(lineElements[posWorkplace]);
                if (Integer.parseInt(lineElements[posLicence]) == 1) {
                    hh.setLicenseHolders(hh.getLicenseHolders() + 1);
                }
                int income = Integer.parseInt(lineElements[posIncome]);
                hh.setIncome(hh.getIncome() + income);
                MitoPerson pp = new MitoPerson(id, hh, occupation, workplace);
                persons.put(pp.getId(), pp);
                hh.addPersonForInitialSetup(pp);
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop person file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("  Finished reading " + recCount + " persons.");
    }


    public void readJobData() {
        logger.info("  Reading job micro data from ascii file");

        String fileName = ResourceUtil.getProperty(rb, PROPERTIES_JJ_FILE_ASCII);

        String recString = "";
        int recCount = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            recString = in.readLine();

            // read header
            String[] header = recString.split(",");
            int posId = MitoUtil.findPositionInArray("id", header);
            int posZone = MitoUtil.findPositionInArray("zone",header);
            int posWorker = MitoUtil.findPositionInArray("personId",header);

            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int id      = Integer.parseInt(lineElements[posId]);
                int zone    = Integer.parseInt(lineElements[posZone]);
                int worker  = Integer.parseInt(lineElements[posWorker]);
                MitoPerson pp = persons.get(worker);
                if (pp.getWorkplace() != id) {
                    logger.error("Person " + worker + " has workplace " + pp.getWorkplace() + " in person file but workplace "
                            + id + " in job file.");
                }
                pp.setWorkzone(zone);
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop job file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("  Finished reading " + recCount + " jobs.");
    }

    public void readInputData() {
        // read all required input data

        purposes = ResourceUtil.getArray(rb, "trip.purposes");
        purposeIndices = new HashMap<>();
        for (int i = 0; i < purposes.length; i++) {
            purposeIndices.put(purposes[i], i);
        }
        // read enrollment data
        TableDataSet enrollmentData = MitoUtil.readCSVfile(rb.getString(PROPERTIES_SCHOOL_ENROLLMENT_FILE));


        for(int i = 0; i < enrollmentData.getRowCount(); i++) {
            int zoneId = enrollmentData.getColumnAsInt(1)[i];
            int enrollment = enrollmentData.getColumnAsInt(2)[i];
            zones.get(zoneId).setSchoolEnrollment(enrollment);
        }

        // define region type of every zone
        defineRegions();
    }

    public void readEmploymentData () {
        // SMZ,State,RET00,OFF00,IND00,OTH00,RET07,OFF07,IND07,OTH07,RET10,OFF10,IND10,OTH10,RET30,OFF30,IND30,OTH30,RET40,OFF40,IND40,OTH40
        TableDataSet employment = MitoUtil.readCSVfile(rb.getString(PROPERTIES_EMPLOYMENT_FILE));
        int[] indEmpl = employment.getColumnAsInt("IND00");
        int[] retEmpl = employment.getColumnAsInt("RET00");
        int[] offEmpl = employment.getColumnAsInt("OFF00");
        int[] othEmpl = employment.getColumnAsInt("OTH00");
        int[] totEmpl = new int[employment.getRowCount()];
        for (int i = 0; i < employment.getRowCount(); i++) {
            totEmpl[i] = indEmpl[i] + retEmpl[i] + offEmpl[i] + othEmpl[i];
            int zoneId = employment.getColumnAsInt("SMZ")[i];
            if(zones.containsKey(zoneId)) {
                Zone zone = zones.get(zoneId);
                zone.setIndEmpl(indEmpl[i]);
                zone.setRetailEmpl(retEmpl[i]);
                zone.setOfficeEmpl(offEmpl[i]);
                zone.setOtherEmpl(othEmpl[i]);
                zone.setTotalEmpl(totEmpl[i]);
            } else {
                logger.warn("Zone " + zoneId +" of employment table not found. Ignoring it.");
            }
        }
    }

    public void readHouseholdTravelSurvey() {
        // read household travel survey

        logger.info("  Reading household travel survey");
        htsHH = MitoUtil.readCSVfile(MitoUtil.getBaseDirectory() + "/" + rb.getString("household.travel.survey.hh"));
        htsTR = MitoUtil.readCSVfile(MitoUtil.getBaseDirectory() + "/" + rb.getString("household.travel.survey.trips"));
    }

    private void defineRegions() {
        TableDataSet regionDefinition = MitoUtil.readCSVfile(rb.getString("household.travel.survey.reg"));
        for(int i = 0; i < regionDefinition.getRowCount(); i++) {
            float id = regionDefinition.getValueAt(i,"Zone");
            int region = reductionNearBorder.getColumnAsInt("Region")[i];
            if(zones.containsKey(id)) {
                zones.get(id).setRegion(region);
            } else {
                logger.warn("Region " + region + " referring to non-existing zone " + id + ". Ignoring it.");
            }
        }
    }

    public int[] defineHouseholdTypeOfEachSurveyRecords(String autoDef, TableDataSet hhTypeDef) {
        // Count number of household records per predefined typ

        int[] hhTypeCounter = new int[MitoUtil.getHighestVal(hhTypeDef.getColumnAsInt("hhType")) + 1];
        int[] hhTypeArray = new int[htsHH.getRowCount() + 1];

        for (int row = 1; row <= htsHH.getRowCount(); row++) {
            int hhSze = (int) htsHH.getValueAt(row, "hhsiz");
            hhSze = Math.min(hhSze, 7);    // hhsiz 8 has only 19 records, aggregate with hhsiz 7
            int hhWrk = (int) htsHH.getValueAt(row, "hhwrk");
            hhWrk = Math.min(hhWrk, 4);    // hhwrk 6 has 1 and hhwrk 5 has 7 records, aggregate with hhwrk 4
            int hhInc = (int) htsHH.getValueAt(row, "incom");
            int hhVeh = (int) htsHH.getValueAt(row, "hhveh");
            hhVeh = Math.min (hhVeh, 3);   // Auto-ownership model will generate groups 0, 1, 2, 3+ only.
            int region = (int) htsHH.getValueAt(row, "urbanSuburbanRural");

            int hhTypeId = getHhType(autoDef, hhTypeDef, hhSze, hhWrk, hhInc, hhVeh, region);
            hhTypeArray[row] = hhTypeId;
            hhTypeCounter[hhTypeId]++;
        }
        // analyze if every household type has a sufficient number of records
        for (int hht = 1; hht < hhTypeCounter.length; hht++) {
            if (hhTypeCounter[hht] < 30) hhTypeArray[0] = -1;  // marker that this hhTypeDef is not worth analyzing
        }
        return hhTypeArray;
    }


    public int getHhType (String autoDef, TableDataSet hhTypeDef, int hhSze, int hhWrk, int hhInc, int hhVeh, int hhReg) {
        // Define household type

        hhSze = Math.min (hhSze, 7);
        hhWrk = Math.min (hhWrk, 4);
        int hhAut;
        if (autoDef.equalsIgnoreCase("autos")) {
            hhAut = Math.min(hhVeh, 3);
        } else {
            if (hhVeh < hhWrk) {
                hhAut = 0;        // fewer autos than workers
            }
            else if (hhVeh == hhWrk) {
                hhAut = 1;  // equal number of autos and workers
            } else {
                hhAut = 2;                      // more autos than workers
            }
        }
        for (int hhType = 1; hhType <= hhTypeDef.getRowCount(); hhType++) {
            if (hhSze >= hhTypeDef.getIndexedValueAt(hhType, "size_l") &&          // Household size
                    hhSze <= hhTypeDef.getIndexedValueAt(hhType, "size_h") &&
                    hhWrk >= hhTypeDef.getIndexedValueAt(hhType, "workers_l") &&   // Number of workers
                    hhWrk <= hhTypeDef.getIndexedValueAt(hhType, "workers_h") &&
                    hhInc >= hhTypeDef.getIndexedValueAt(hhType, "income_l") &&    // Household income
                    hhInc <= hhTypeDef.getIndexedValueAt(hhType, "income_h") &&
                    hhAut >= hhTypeDef.getIndexedValueAt(hhType, "autos_l") &&     // Number of vehicles
                    hhAut <= hhTypeDef.getIndexedValueAt(hhType, "autos_h") &&
                    hhReg >= hhTypeDef.getIndexedValueAt(hhType, "region_l") &&    // Region (urban, suburban, rural)
                    hhReg <= hhTypeDef.getIndexedValueAt(hhType, "region_h")) {
                return (int) hhTypeDef.getIndexedValueAt(hhType, "hhType");
            }
        }
        logger.error ("Could not define household type: " + hhSze + " " + hhWrk + " " + hhInc + " " + hhVeh + " " + hhReg);
        for (int hhType = 1; hhType <= hhTypeDef.getRowCount(); hhType++) {
            logger.error(hhType+": "+hhTypeDef.getIndexedValueAt(hhType, "size_l")+"-"+hhTypeDef.getIndexedValueAt(hhType, "size_h")
                    +","+hhTypeDef.getIndexedValueAt(hhType, "workers_l")+"-"+hhTypeDef.getIndexedValueAt(hhType, "workers_h")
                    +","+hhTypeDef.getIndexedValueAt(hhType, "income_l")+"-"+hhTypeDef.getIndexedValueAt(hhType, "income_h")
                    +","+hhTypeDef.getIndexedValueAt(hhType, "autos_l")+"-"+hhTypeDef.getIndexedValueAt(hhType, "autos_h")
                    +","+hhTypeDef.getIndexedValueAt(hhType, "region_l")+"-"+hhTypeDef.getIndexedValueAt(hhType, "region_h"));
        }
        return -1;
    }


    public HashMap<String, Integer[]> collectTripFrequencyDistribution (int[] hhTypeArray) {
        // Summarize frequency of number of trips for each household type by each trip purpose
        //
        // Storage Structure
        //   HashMap<String, Integer> tripsByHhTypeAndPurpose: Token is hhType_TripPurpose
        //   |
        //   contains -> Integer[] tripFrequencyList: Frequency of 0, 1, 2, 3, ... trips

        HashMap<String, Integer[]> tripsByHhTypeAndPurpose = new HashMap<>();  // contains trips by hhtype and purpose

        for (int hhType = 1; hhType < hhTypeArray.length; hhType++) {
            for (String purp: purposes) {
                String token = String.valueOf(hhType) + "_" + purp;
                // fill Storage structure from bottom       0                  10                  20                  30
                Integer[] tripFrequencyList = new Integer[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};  // space for up to 30 trips
                tripsByHhTypeAndPurpose.put(token, tripFrequencyList);
            }
        }

        // Read through household file of HTS
        int pos = 1;
        for (int hhRow = 1; hhRow <= htsHH.getRowCount(); hhRow++) {
            int sampleId = (int) htsHH.getValueAt(hhRow, "sampn");
            int hhType = hhTypeArray[hhRow];
            int[] tripsOfThisHouseholdByPurposes = new int[purposes.length];
            // Ready through trip file of HTS
            for (int trRow = pos; trRow <= htsTR.getRowCount(); trRow++) {
                if ((int) htsTR.getValueAt(trRow, "sampn") == sampleId) {

                    // add this trip to this household
                    pos++;
                    String htsTripPurpose = htsTR.getStringValueAt(trRow, "mainPurpose");
                    tripsOfThisHouseholdByPurposes[MitoUtil.findPositionInArray(htsTripPurpose, purposes)]++;
                } else {
                    // This trip record does not belong to this household
                    break;
                }
            }
            for (int p = 0; p < purposes.length; p++) {
                String token = String.valueOf(hhType) + "_" + purposes[p];
                Integer[] tripsOfThisHouseholdType = tripsByHhTypeAndPurpose.get(token);
                int count = tripsOfThisHouseholdByPurposes[p];
                tripsOfThisHouseholdType[count]++;
                tripsByHhTypeAndPurpose.put(token, tripsOfThisHouseholdType);
            }
        }
        return tripsByHhTypeAndPurpose;
    }
}
