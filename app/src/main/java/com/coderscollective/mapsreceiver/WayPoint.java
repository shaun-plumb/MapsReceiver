package com.coderscollective.mapsreceiver;

public class WayPoint {

    @Override
    public String toString() {
        return "WayPoint{" +
                "lat=" + lat +
                ", lon=" + lon +
                ", silent=" + silent +
                ", name='" + name + '\'' +
                '}';
    }

    public WayPoint(float lat, float lon, boolean silent) {
        this.lat = lat;
        this.lon = lon;
        this.silent = silent;
    }

    float lat;

    public WayPoint(float lat, float lon, boolean silent, String name) {
        this.lat = lat;
        this.lon = lon;
        this.silent = silent;
        this.name = name;
    }

    float lon;
    boolean silent;
    String name;

}
