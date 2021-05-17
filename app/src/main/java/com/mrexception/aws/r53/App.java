package com.mrexception.aws.r53;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) throws Exception {
        var app = new App();

        app.readEnv();
        app.cleanRecordName();
        app.getExternalIp();
        app.buildClient();
        //app.listHostedZones();
        if (app.shouldUpdateRecord()) {
            app.updateRecord();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private AmazonRoute53 client;
    private String ip;

    private String hostedZone;
    private String recordName;

    // TODO: do these need to be configurable?
    private final long TTL = 60L;
    private final RRType type = RRType.A;

    private void readEnv() {
        LOG.info("Beginning configuration setup.");

        hostedZone = System.getenv("HOSTED_ZONE");
        recordName = System.getenv("RECORD_NAME");

        String accessKey = System.getenv("AWS_ACCESS_KEY");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String region = System.getenv("AWS_REGION");

        boolean success = true;
        if (hostedZone == null || hostedZone.isBlank()) {
            LOG.error("HOSTED_ZONE environment variable is required.");
            success = false;
        }

        if (recordName == null || recordName.isBlank()) {
            LOG.error("RECORD_NAME environment variable is required.");
            success = false;
        }

        if (accessKey == null || accessKey.isBlank()) {
            LOG.error("AWS_ACCESS_KEY environment variable is required.");
            success = false;
        }

        if (secretKey == null || secretKey.isBlank()) {
            LOG.error("AWS_SECRET_ACCESS_KEY environment variable is required.");
            success = false;
        }

        if (region == null || region.isBlank()) {
            LOG.error("AWS_REGION environment variable is required.");
            success = false;
        }

        if (!success) {
            LOG.error("Missing one or more required Environment variables. Exiting.");
            System.exit(1);
        }

        LOG.info("Configuration set up.");
    }

    /**
     * Cleans up the RECORD_NAME input to be a valid name for Route53.
     * See:
     * - https://forums.aws.amazon.com/thread.jspa?threadID=113183
     * - https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/DomainNameFormat.html#domain-name-format-asterisk
     * - https://docs.aws.amazon.com/Route53/latest/APIReference/API_ListResourceRecordSets.html#API_ListResourceRecordSets_Responses
     */
    private void cleanRecordName() {
        String cleanedRecordName = recordName.replaceAll("\\*", "\\\\052");
        if(!recordName.equals(cleanedRecordName)) {
            LOG.debug("RECORD_NAME updated '{}' -> '{}'", recordName, cleanedRecordName);
            recordName = cleanedRecordName;
        }
    }

    private void buildClient() {
        LOG.info("Building AWS client.");

        var credentialsProvider = new EnvironmentVariableCredentialsProvider();

        client = AmazonRoute53ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .build();

        LOG.info("AWS Client built.");
    }

    private void listHostedZones() {
        ListHostedZonesByNameResult listHostedZonesByNameResult = client.listHostedZonesByName();

        for (HostedZone hostedZone : listHostedZonesByNameResult.getHostedZones()) {
            LOG.debug("Found hosted zone {} -> {}", hostedZone.getName(), hostedZone.getId());
        }
    }

    private boolean shouldUpdateRecord() {
        ListResourceRecordSetsRequest request = new ListResourceRecordSetsRequest(hostedZone);
        ListResourceRecordSetsResult listResourceRecordSetsResult = client.listResourceRecordSets(request);

        boolean ipAddressUnchanged = listResourceRecordSetsResult.getResourceRecordSets()
                .stream()
                .filter(resourceRecordSet -> resourceRecordSet.getName().equals(recordName))
                .flatMap(resourceRecordSet -> resourceRecordSet.getResourceRecords().stream())
                .map(ResourceRecord::getValue)
                .anyMatch(recordIp -> recordIp.equals(ip));

        if (ipAddressUnchanged) {
            LOG.info("HostedZone {} record {} already set to ip {}, will not change", hostedZone, recordName, ip);
        } else {
            LOG.info("HostedZone {} record {} needs to be updated, will change", hostedZone, recordName);
        }
        return !ipAddressUnchanged;
    }

    private void getExternalIp() throws Exception {
        LOG.info("Finding external IP.");

        URL whatismyip = new URL("https://checkip.amazonaws.com");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            this.ip = in.readLine();

            LOG.info("External IP: {}", ip);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        LOG.info("Found external IP.");
    }

    private void updateRecord() {
        LOG.info("Updating Route53 Record.");

        ResourceRecord rr = new ResourceRecord(ip);
        List<ResourceRecord> rrList = new ArrayList<>();
        rrList.add(rr);

        // Create a ResourceRecordSet
        ResourceRecordSet resourceRecordSet = new ResourceRecordSet();
        resourceRecordSet.setName(recordName);
        resourceRecordSet.setType(type);
        resourceRecordSet.setTTL(TTL);
        resourceRecordSet.setResourceRecords(rrList);

        // Create a change
        Change change = new Change(ChangeAction.UPSERT, resourceRecordSet);
        List<Change> changesList = new ArrayList<>();
        changesList.add(change);

        ChangeBatch changeBatch = new ChangeBatch(changesList);
        changeBatch.setComment("Automatic DNS Update");

        ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest(hostedZone, changeBatch);

        ChangeResourceRecordSetsResult result = client.changeResourceRecordSets(request);

        LOG.info(result.getChangeInfo().toString());

        LOG.info("Route53 Record Updated.");
    }
}
