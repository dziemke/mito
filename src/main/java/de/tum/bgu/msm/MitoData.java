package de.tum.bgu.msm;

import com.pb.common.datafile.TableDataSet;
import com.pb.common.matrix.Matrix;
import com.pb.common.util.ResourceUtil;
import omx.OmxFile;
import omx.OmxMatrix;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Random;
import java.util.ResourceBundle;

/**
 * Holds data for the Transport in Microsimulation Orchestrator (TIMO)
 * @author Rolf Moeckel
 * Created on Sep 18, 2016 in Munich, Germany
 *
 */

public class MitoData {

    protected static final String PROPERTIES_ZONAL_DATA_FILE          = "zonal.data.file";
    protected static final String PROPERTIES_AUTO_PEAK_SKIM           = "auto.peak.sov.skim";
    protected static final String PROPERTIES_TRANSIT_PEAK_SKIM        = "transit.peak.time";
    protected static final String PROPERTIES_HH_FILE_ASCII            = "household.file.ascii";
    protected static final String PROPERTIES_PP_FILE_ASCII            = "person.file.ascii";

    private static Logger logger = Logger.getLogger(MitoData.class);
    private ResourceBundle rb;
    private int[] zones;
    private int[] zoneIndex;
    private float[] sizeOfZonesInAcre;
    private MitoHousehold[] mitoHouseholds;
    private int[] householdsByZone;
    private int[] retailEmplByZone;
    private int[] officeEmplByZone;
    private int[] otherEmplByZone;
    private int[] totalEmplByZone;
    private int[] schoolEnrollmentByZone;
    private Matrix autoTravelTimes;
    private Matrix transitTravelTimes;
    private TableDataSet htsHH;
    private TableDataSet htsTR;
    private String[] purposes;
    private Random rand;


    public MitoData(ResourceBundle rb) {
        this.rb = rb;
        initializeRandomNumber();
    }


    private void initializeRandomNumber() {
        // initialize random number generator
        int seed = ResourceUtil.getIntegerProperty(rb, "random.seed");
        if (seed == -1)
            rand = new Random();
        else
            rand = new Random(seed);
    }


    public Random getRand () {
        return rand;
    }


    public void readZones () {
        // read in zones from file
        String fileName = ResourceUtil.getProperty(rb, PROPERTIES_ZONAL_DATA_FILE);
        TableDataSet zonalData = MitoUtil.readCSVfile(fileName);
        //zonalData.buildIndex(zonalData.getColumnPosition("ZoneId"));

        zones = zonalData.getColumnAsInt("ZoneId");
        zoneIndex = MitoUtil.createIndexArray(zones);
    }


    public void setZones(int[] zones) {
        this.zones = zones;
        zoneIndex = MitoUtil.createIndexArray(zones);
    }

    public int[] getZones() {
        return zones;
    }

    public int getZoneIndex(int zone) {
        return zoneIndex[zone];
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


    public void setHouseholds(MitoHousehold[] mitoHouseholds) {
        // store households in memory as MitoHousehold objects
        this.mitoHouseholds = mitoHouseholds;
        // fill householdsByZone array
        householdsByZone = new int[getZones().length];
        for (MitoHousehold thh: mitoHouseholds) householdsByZone[getZoneIndex(thh.getHomeZone())]++;
    }

    public MitoHousehold[] getMitoHouseholds() {
        return mitoHouseholds;
    }

    public int getHouseholdsByZone (int zone) {
        return householdsByZone[getZoneIndex(zone)];
    }


    public void readHouseholdData() {
        logger.info("  Reading household micro data from ascii file");

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
                int dwellingID = Integer.parseInt(lineElements[posDwell]);
                int taz        = Integer.parseInt(lineElements[posTaz]);
                int hhSize     = Integer.parseInt(lineElements[posSize]);
                int autos      = Integer.parseInt(lineElements[posAutos]);

                new MitoHousehold(id, hhSize, 0, 0, autos, taz);
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop household file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("  Finished reading " + recCount + " households.");
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
            int posHhId = MitoUtil.findPositionInArray("hhid",header);
            int posOccupation = MitoUtil.findPositionInArray("occupation",header);
            int posIncome = MitoUtil.findPositionInArray("income",header);

            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int hhid = Integer.parseInt(lineElements[posHhId]);
                MitoHousehold hh = MitoHousehold.getHouseholdFromId(hhid);
                if (Integer.parseInt(lineElements[posOccupation]) == 1) {
                    hh.setNumberOfWorkers(hh.getNumberOfWorkers() + 1);
                }
                int income = Integer.parseInt(lineElements[posIncome]);
                hh.setIncome(hh.getIncome() + income);
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop household file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("  Finished reading " + recCount + " persons.");
    }



    public void readInputData() {
        // read all required input data
        readHouseholdTravelSurvey();
        purposes = ResourceUtil.getArray(rb, "trip.purposes");
        // create placeholder for number of trips by purpose for every household
        for (MitoHousehold thh: mitoHouseholds) thh.createTripByPurposeArray(purposes.length);

        // read enrollment data
        TableDataSet enrollmentData = MitoUtil.readCSVfile(rb.getString("school.enrollment.data"));
        schoolEnrollmentByZone = new int[getZones().length];
        for (int row = 1; row <= enrollmentData.getRowCount(); row++) {
            schoolEnrollmentByZone[getZoneIndex((int) enrollmentData.getValueAt(row, "SMZ_N"))] =
                    (int) enrollmentData.getValueAt(row, "ENR");
        }
    }

    public void setRetailEmplByZone(int[] retailEmplByZone) {
        this.retailEmplByZone = retailEmplByZone;
    }

    public int getRetailEmplByZone (int zone) {
        return retailEmplByZone[zone];
    }

    public void setOfficeEmplByZone(int[] officeEmplByZone) {
        this.officeEmplByZone = officeEmplByZone;
    }

    public int getOfficeEmplByZone(int zone) {
        return officeEmplByZone[zone];
    }

    public void setOtherEmplByZone(int[] otherEmplByZone) {
        this.otherEmplByZone = otherEmplByZone;
    }

    public int getOtherEmplByZone (int zone) {
        return otherEmplByZone[zone];
    }

    public void setTotalEmplByZone(int[] totalEmplByZone) {
        this.totalEmplByZone = totalEmplByZone;
    }

    public int getTotalEmplByZone (int zone) {
        return totalEmplByZone[zone];
    }

    public int getSchoolEnrollmentByZone (int zone) {
        return schoolEnrollmentByZone[zone];
    }

    public void setSizeOfZonesInAcre(float[] sizeOfZonesInAcre) {
        this.sizeOfZonesInAcre = sizeOfZonesInAcre;
    }

    public float getSizeOfZoneInAcre (int zone) {
        return sizeOfZonesInAcre[zone];
    }

    private void readHouseholdTravelSurvey() {
        // read household travel survey

        logger.info("  Reading household travel survey");
        htsHH = MitoUtil.readCSVfile(rb.getString("household.travel.survey.hh"));
        htsTR = MitoUtil.readCSVfile(rb.getString("household.travel.survey.trips"));
    }


    public String[] getPurposes () {
        return purposes;
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
            if (hhVeh < hhWrk) hhAut = 0;        // fewer autos than workers
            else if (hhVeh == hhWrk) hhAut = 1;  // equal number of autos and workers
            else hhAut = 2;                      // more autos than workers
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
            System.out.println(hhType+": "+hhTypeDef.getIndexedValueAt(hhType, "size_l")+"-"+hhTypeDef.getIndexedValueAt(hhType, "size_h")
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


    public int getTotalNumberOfTripsGeneratedByPurpose (int purpose) {
        // sum up trips generated by purpose

        int prodSum = 0;
        for (MitoHousehold thh: getMitoHouseholds()) {
            prodSum += thh.getNumberOfTrips(purpose);
        }
        return prodSum;
    }

}