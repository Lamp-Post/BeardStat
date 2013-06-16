package com.tehbeard.BeardStat.DataProviders;

public class StatisticMetadata {

    public enum Formatting {
        none, time, timestamp
    }

    private int        id;

    private String     name;

    private String     localizedName;

    private Formatting format;

    public StatisticMetadata(int id, String name, String localizedName, Formatting format) {
        this.id = id;
        this.name = name;
        this.localizedName = localizedName;
        this.format = format;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalizedName() {
        return this.localizedName;
    }

    public void setLocalizedName(String localizedName) {
        this.localizedName = localizedName;
    }

    public Formatting getFormat() {
        return this.format;
    }

    public void setFormat(Formatting format) {
        this.format = format;
    }

}