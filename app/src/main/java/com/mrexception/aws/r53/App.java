package com.mrexception.aws.r53;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.ListHostedZonesByNameResult;

public class App {
    public static void main(String[] args) {
        var app = new App();

        var client = app.buildClient();

        ListHostedZonesByNameResult listHostedZonesByNameResult = client.listHostedZonesByName();

        for (HostedZone hostedZone : listHostedZonesByNameResult.getHostedZones()) {
            log(String.format("%s -> %s", hostedZone.getId(), hostedZone.getName()));
        }
    }

    public AmazonRoute53 buildClient() {
        var credentialsProvider = new EnvironmentVariableCredentialsProvider();

        return AmazonRoute53ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .build();
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}
