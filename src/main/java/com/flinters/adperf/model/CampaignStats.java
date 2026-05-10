package com.flinters.adperf.model;

public class CampaignStats {
    public final String campaignId;
    public long totalImpressions;
    public long totalClicks;
    public double totalSpend;
    public long totalConversions;

    public CampaignStats(String campaignId) {
        this.campaignId = campaignId;
    }

    public void accumulate(long impressions, long clicks, double spend, long conversions) {
        totalImpressions += impressions;
        totalClicks += clicks;
        totalSpend += spend;
        totalConversions += conversions;
    }
}
