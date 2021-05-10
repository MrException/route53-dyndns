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

        boolean envSet = app.readEnv();

        if (!envSet) {
            System.exit(1);
        }

        app.getExternalIp();

        app.buildClient();

        app.listHostedZones();

        app.updateRecord();
    }

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private AmazonRoute53 client;
    private String ip;

    private String hostedZone;
    private String recordName;

    // TODO: do these need to be configurable?
    private final long TTL = 60L;
    private final RRType type = RRType.A;

    private boolean readEnv() {
        hostedZone = System.getenv("HOSTED_ZONE");
        recordName = System.getenv("RECORD_NAME");

        boolean success = true;
        if (hostedZone == null || hostedZone.isBlank()) {
            LOG.error("HOSTED_ZONE environment variable is required.");
            success = false;
        }

        if (recordName == null || recordName.isBlank()) {
            LOG.error("RECORD_NAME environment variable is required.");
            success = false;
        }

        return success;
    }


    public void buildClient() {
        var credentialsProvider = new EnvironmentVariableCredentialsProvider();

        client = AmazonRoute53ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .build();
    }

    private void listHostedZones() {
        ListHostedZonesByNameResult listHostedZonesByNameResult = client.listHostedZonesByName();

        for (HostedZone hostedZone : listHostedZonesByNameResult.getHostedZones()) {
            LOG.debug("Found hosted zone {} -> {}", hostedZone.getName(), hostedZone.getId());
        }
    }

    public void getExternalIp() throws Exception {
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
    }

    public void updateRecord() {
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
    }
}
