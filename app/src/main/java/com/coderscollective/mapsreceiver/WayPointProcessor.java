package com.coderscollective.mapsreceiver;

import java.util.ArrayList;
import java.util.List;

public class WayPointProcessor {

    public static List<WayPoint> extractWayPoints(String pb) {
        List<WayPoint> wayPoints = new ArrayList<>();
        String[] tokens = pb.split("!");

        Float lat = null;
        Float lon = null;
        boolean silent = false;

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            // 3m4 is the marker for a silent waypoint
            if (token.startsWith("3m4")) {
                silent = true;
            }

            try {
                // Based on observation:
                // 1d: Longitude
                // 2d: Latitude
                // 3d: Latitude
                // 4d: Longitude
                if (token.startsWith("1d")) {
                    lon = Float.parseFloat(token.substring(2));
                } else if (token.startsWith("2d")) {
                    lat = Float.parseFloat(token.substring(2));
                } else if (token.startsWith("3d")) {
                    lat = Float.parseFloat(token.substring(2));
                    // 3d/4d format seems to be always non-silent (named)
                    silent = false;
                } else if (token.startsWith("4d")) {
                    lon = Float.parseFloat(token.substring(2));
                    silent = false;
                }
            } catch (NumberFormatException ignored) {}

            if (lat != null && lon != null) {
                wayPoints.add(new WayPoint(lat, lon, silent));
                lat = null;
                lon = null;
                silent = false; // Reset for next waypoint
            }
        }
        return wayPoints;
    }
}



