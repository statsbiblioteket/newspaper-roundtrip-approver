package dk.statsbiblioteket.newspaper.roundtripapprover;

import dk.statsbiblioteket.medieplatform.autonomous.Batch;
import dk.statsbiblioteket.medieplatform.autonomous.ConfigConstants;
import dk.statsbiblioteket.medieplatform.autonomous.DomsEventStorage;
import dk.statsbiblioteket.medieplatform.autonomous.DomsEventStorageFactory;
import dk.statsbiblioteket.medieplatform.autonomous.Event;
import dk.statsbiblioteket.medieplatform.autonomous.ResultCollector;
import dk.statsbiblioteket.medieplatform.autonomous.TreeProcessorAbstractRunnableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 *  Component class which decides whether or not to approve a specific roundtrip after a batch has been
 *  approved in MFPak.
 */
public class RoundtripApproverComponent extends TreeProcessorAbstractRunnableComponent {

    private Logger log = LoggerFactory.getLogger(getClass());

    public static String ROUNDTRIP_APPROVED_EVENT = "Roundtrip_Approved";

    protected RoundtripApproverComponent(Properties properties) {
        super(properties);
    }

    @Override
    public String getEventID() {
        return ROUNDTRIP_APPROVED_EVENT;
    }

    @Override
    public void doWorkOnBatch(Batch batch, ResultCollector resultCollector) throws Exception {
        log.info("Checking approval for '{}'", batch.getFullID());
        Properties properties = getProperties();
        DomsEventStorageFactory domsEventStorageFactory = new DomsEventStorageFactory();
        domsEventStorageFactory.setFedoraLocation(properties.getProperty(ConfigConstants.DOMS_URL));
        domsEventStorageFactory.setUsername(properties.getProperty(ConfigConstants.DOMS_USERNAME));
        domsEventStorageFactory.setPassword(properties.getProperty(ConfigConstants.DOMS_PASSWORD));
        DomsEventStorage domsEventStorage = domsEventStorageFactory.createDomsEventStorage();
        List<Batch> allRoundTrips = domsEventStorage.getAllRoundTrips(batch.getBatchID());
        int maxQaFlagged = getMaxRoundtripQAFlagged(allRoundTrips);
        if (batch.getRoundTripNumber() == maxQaFlagged) {
            log.info("Approved round trip number {} for batch {}.", maxQaFlagged, batch.getBatchID());
        } else if (batch.getRoundTripNumber() < maxQaFlagged) {
            resultCollector.addFailure(batch.getFullID(),
                    "exception",
                    getClass().getSimpleName(),
                    "Round trip is superseded by a later round trip (RT" + maxQaFlagged + ") which is approved.");
        } else if (batch.getRoundTripNumber() > maxQaFlagged) {
            resultCollector.addFailure(batch.getFullID(),
                    "exception",
                    getClass().getSimpleName(),
                    "Round trip is preceded by an earlier round trip (RT" + maxQaFlagged + ") which has been approved.");
            domsEventStorage.addEventToBatch(batch.getBatchID(), batch.getRoundTripNumber(), getClass().getSimpleName(),
                    new Date(),
                    "An earlier Roundtrip for this batch has already been approved.", "Manually_Stopped", true);
        }
    }


    /**
     * Returns the roundtrip number of the highest roundtrip which has been flagged for Manual QA.
     * @param roundtrips A list of Batch objects sorted in ascending roundtrip number.
     * @return the maximum roundtrip number for which the Manual QA flag is set.
     */
    private int getMaxRoundtripQAFlagged(List<Batch> roundtrips) {
        int max = 0;
        for (Batch roundtrip: roundtrips) {  //because roundtrips are sorted.
            for (Event event: roundtrip.getEventList()) {
                if (event.getEventID().equals("Manual_QA_Flagged")) {
                    max = roundtrip.getRoundTripNumber();
                }
            }
        }
        return max;
    }
}