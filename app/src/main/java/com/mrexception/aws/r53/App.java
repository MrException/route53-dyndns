package com.mrexception.aws.r53;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) throws Exception {
        var app = new App();

        app.log(String.format("External IP: %s", app.getExternalIp()));

        app.buildClient();

        app.listHostedZones();

        app.updateRecord();
    }

    private AmazonRoute53 client;
    private String ip;

    // TODO: pull these from ENV
    private String hostedZone = "Z3GAVLTARPYGKC";
    private String recordName = "test.robmcbride.dev";

    // TODO: do these need to be configurable?
    private long TTL = 60L;
    private RRType type = RRType.A;

    public void buildClient() {
        var credentialsProvider = new EnvironmentVariableCredentialsProvider();

        client = AmazonRoute53ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .build();
    }

    private void listHostedZones() {
        ListHostedZonesByNameResult listHostedZonesByNameResult = client.listHostedZonesByName();

        for (HostedZone hostedZone : listHostedZonesByNameResult.getHostedZones()) {
            log(String.format("%s -> %s", hostedZone.getId(), hostedZone.getName()));
        }
    }

    public String getExternalIp() throws Exception {
        URL whatismyip = new URL("https://checkip.amazonaws.com");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            this.ip = in.readLine();
            return this.ip;
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

        log(result.getChangeInfo().toString());
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
