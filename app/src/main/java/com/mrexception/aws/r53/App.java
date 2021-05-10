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
    }

    private AmazonRoute53 client;

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
            return in.readLine();
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

    public void updateRecord(String zoneId, String ip, String domain) {
        ResourceRecord rr = new ResourceRecord(ip);
        List<ResourceRecord> rrList = new ArrayList<>();
        rrList.add(rr);

        // Create a ResourceRecordSet
        ResourceRecordSet resourceRecordSet = new ResourceRecordSet();
        resourceRecordSet.setName(domain);
        resourceRecordSet.setType(RRType.A);
        resourceRecordSet.setTTL(300L);
        resourceRecordSet.setWeight(0L);
        resourceRecordSet.setResourceRecords(rrList);

        // Create a change
        Change change = new Change(ChangeAction.CREATE, resourceRecordSet);
        List<Change> changesList = new ArrayList<>();
        changesList.add(change);

        ChangeBatch changeBatch = new ChangeBatch(changesList);

        ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest(zoneId, changeBatch);

        ChangeResourceRecordSetsResult result = client.changeResourceRecordSets(request);

        log(result.getChangeInfo().toString());
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
