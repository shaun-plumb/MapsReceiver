package com.coderscollective.mapsreceiver;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileProcessorService extends Service {
    private static final String TAG = "MapsReceiverService";
    private static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    static {
        // Fix for java.net.ConnectException with IPv6: Prefer IPv4
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    // The API key is now loaded from local.properties via BuildConfig
    private static final String API_KEY = BuildConfig.MAPS_API_KEY;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String sharedText = intent.getStringExtra("shared_text");
            if (sharedText != null) {
                Log.d(TAG, "Received shared text: " + sharedText);
                new Thread(() -> {
                    try {
                        String sharedURL = extractUrl(sharedText);
                        if (sharedURL == null) {
                            Log.d(TAG, "No URL found in text");
                            return;
                        }
                        Log.d(TAG, "Extracted URL: " + sharedURL);

                        // Follow redirects to get the final URL
                        String finalUrl = followRedirects(sharedURL);
                        Log.d(TAG, "Final URL: " + finalUrl);

                        // Google place URL - extract coordinates or call GM API
                        if (finalUrl.contains("/maps/place/")) {
                            sharePlaceURL(finalUrl);
                            showToast("Shared Place");
                        } else if (finalUrl.contains("/maps/dir")) {
                            shareRouteGPX(finalUrl);
                            showToast("Shared Route");
                        } else {
                            Log.w(TAG, "URL not recognized: " + finalUrl);
                            showToast("URL not recognized");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error in processing", e);
                        showToast("Error in processing: " + e.getMessage());
                    } finally {
                        stopSelf();
                    }
                }).start();
            }
        }
        return START_NOT_STICKY;
    }

    private void shareRouteGPX(String finalUrl) {
        String[] urlParts = finalUrl.split("/");
        String[] routeParts = Arrays.copyOfRange(urlParts, 5, urlParts.length - 1);
        String pb_string = urlParts[urlParts.length - 1].split("\\?")[0].substring(5);

        List<WayPoint> waypoints = WayPointProcessor.extractWayPoints(pb_string);

        // if this is a 'current position to ...' route, then the pb_string in the URL only contains the destination (and waypoints)
        // so if we only get fewer named waypoints back than expected, we need to add the current position as the first waypoint
        if (waypoints.stream().filter(wp -> !wp.silent).count() < routeParts.length) {
            // Check if routeParts[0] is a coordinate pair (lat,lon)
            if (routeParts[0].matches("^-?\\d+(\\.\\d+)?, -?\\d+(\\.\\d+)?$") || routeParts[0].matches("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?$")) {
                String[] coords = routeParts[0].split(",");
                try {
                    float lat = Float.parseFloat(coords[0].trim());
                    float lon = Float.parseFloat(coords[1].trim());
                    waypoints.add(0, new WayPoint(lat, lon, false));
                    routeParts[0] = "Current Position";
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing coordinates", e);
                    showToast("Error parsing coordinates");
                }
            } else {
                Log.e(TAG, "Invalid coordinate pair in URL: " + routeParts[0]);
                showToast("Invalid coordinate pair in URL");
            }
        }

        AtomicInteger index = new AtomicInteger();
        waypoints.stream().filter(wp -> !wp.silent).forEach(wp -> wp.name = routeParts[index.getAndIncrement()]);

        String routeName = routeParts[0].replace("+", " ") + " to " + routeParts[routeParts.length - 1].replace("+", " ");

        // Create the GPX file in the cache directory
        File cacheDir = new File(getCacheDir(), "gpx");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File gpxFile = new File(cacheDir, routeName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".gpx");

//        File gpxFile = new File(getExternalFilesDir("external_files"), routeName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".gpx");
        
        File savedFile = GPXGenerator.createGPX(gpxFile, routeName, waypoints);
        
        if (savedFile != null && savedFile.exists()) {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", savedFile);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            String mime = "application/gpx+xml";
            shareIntent.setType(mime);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shareIntent.setPackage("com.garmin.android.apps.phonelink");

            try {
                startActivity(shareIntent);
            } catch (android.content.ActivityNotFoundException e) {
                Log.e(TAG, "Garmin app not found, showing chooser");
                showToast("Garmin app not found");
                Intent chooser = Intent.createChooser(shareIntent, "Open GPX with...");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            }
        }
    }

    private void sharePlaceURL(String finalUrl) {
        // Try to find coordinates in the URL
        String coords = extractCoords(finalUrl);

        // If not in URL, check the API or Page
        if (coords == null) {
            Log.d(TAG, "Coords not in URL, trying Places API...");
            coords = fetchCoordsFromAPI(finalUrl);
        }

        if (coords != null) {
            shareCoordinates(coords);
        } else {
            Log.w(TAG, "FAILED: Coordinates not found.");
            showToast("Failed: Coordinates not found");
        }
    }

    private void shareCoordinates(String coords) {
        if (coords == null) return;
        Log.i(TAG, "SUCCESS! Sharing Coordinates: " + coords);
        Intent intGpx = new Intent(Intent.ACTION_SEND);
        intGpx.setPackage("com.garmin.android.apps.phonelink");
        intGpx.setType("text/plain");
        intGpx.putExtra(Intent.EXTRA_TEXT, coords);
        intGpx.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intGpx);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "Garmin app not found", e);
            showToast("Garmin app not found");
            // Fallback: show share sheet if Garmin is missing
            Intent chooser = Intent.createChooser(intGpx, "Share Coordinates");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
        }
    }

    private String fetchCoordsFromAPI(String urlString) {
        if ("YOUR_API_KEY_HERE".equals(API_KEY)) {
            Log.w(TAG, "API Key not set. Skipping API call.");
            return null;
        }

        try {
            // Extract the hex CID/FID from the URL
            // Format: !1s0x[hex]:0x[hex]
            String decodedUrl = java.net.URLDecoder.decode(urlString, "UTF-8");
            // Fixed regex: [a-fA-F0-9]
            Pattern p = Pattern.compile("0x[a-fA-F0-9]+:0x([a-fA-F0-9]+)");
            Matcher m = p.matcher(decodedUrl);
            
            if (m.find()) {
                String fidHex = m.group(1);
                // Convert the hex FID (second part) to decimal CID
                long cidDecimal = Long.parseUnsignedLong(fidHex, 16);
                Log.d(TAG, "Converted FID " + fidHex + " to decimal CID: " + cidDecimal);

                // Use the cid parameter in the Place Details API
                String apiUrl = "https://maps.googleapis.com/maps/api/place/details/json" +
                        "?cid=" + cidDecimal +
                        "&fields=geometry,name" +
                        "&key=" + API_KEY;

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int responseCode = conn.getResponseCode();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode == 200 ? conn.getInputStream() : conn.getErrorStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    String json = response.toString();
                    
                    if (responseCode == 200) {
                        Log.d(TAG, "API Response: " + json);
                        Pattern pLat = Pattern.compile("\"lat\"\\s*:\\s*(-?\\d+\\.\\d+)");
                        Pattern pLon = Pattern.compile("\"lng\"\\s*:\\s*(-?\\d+\\.\\d+)");
                        Matcher mLat = pLat.matcher(json);
                        Matcher mLon = pLon.matcher(json);
                        
                        if (mLat.find() && mLon.find()) {
                            return mLat.group(1) + "," + mLon.group(1);
                        } else if (json.contains("ZERO_RESULTS")) {
                            Log.w(TAG, "Places API returned ZERO_RESULTS for CID: " + cidDecimal);
                        }
                    } else {
                        Log.e(TAG, "Places API Error (" + responseCode + "): " + json);
                    }
                }
            } else {
                Log.w(TAG, "Could not find 0x:0x pattern in URL: " + decodedUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calling Places API", e);
        }
        return null;
    }

    private String extractUrl(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("https://\\S+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String followRedirects(String urlString) throws Exception {
        String currentUrl = urlString;
        String mobileUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";

        for (int i = 0; i < 12; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            try {
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(false);

                // Short links often 404 with Desktop UA or when specific cookies are present
                if (currentUrl.contains("goo.gl")) {
                    conn.setRequestProperty("User-Agent", mobileUA);
                    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                } else {
                    conn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
                    if (currentUrl.contains("google.")) {
                        conn.setRequestProperty("Cookie", "SOCS=CAISHAgBEhJnd3NfMm9yZF9tYXBzXzIwMjQwNDA3X1JDMxoBZW4gACIBGgA; CONSENT=YES+");
                    }
                }

                conn.connect();
                int status = conn.getResponseCode();
                Log.d(TAG, "Hop " + i + " [" + status + "]: " + currentUrl);

                // Handle 404 for short links specifically
                if (status == 404 && currentUrl.contains("goo.gl")) {
                    Log.w(TAG, "Short link 404, retrying with platform default...");
                    conn.disconnect();
                    HttpURLConnection retryConn = (HttpURLConnection) new URL(currentUrl).openConnection();
                    retryConn.setInstanceFollowRedirects(true);
                    retryConn.setConnectTimeout(10000);
                    retryConn.connect();
                    int retryStatus = retryConn.getResponseCode();
                    String redirectedUrl = retryConn.getURL().toString();
                    retryConn.disconnect();
                    Log.d(TAG, "Retry result [" + retryStatus + "]: " + redirectedUrl);
                    if (retryStatus < 400 && !redirectedUrl.equals(currentUrl)) {
                        return redirectedUrl;
                    }
                }

                // Check if coordinates are ALREADY in the URL before continuing
                // Only exit early for place/point URLs. For routes (/maps/dir), we MUST reach 
                // the final URL to get the full route data (pb string).
                if (!currentUrl.contains("/maps/dir")) {
                    String coords = extractCoords(currentUrl);
                    if (coords != null) {
                        Log.d(TAG, "Found coordinates in redirect URL: " + coords);
                        return currentUrl;
                    }
                }

                if (status >= 300 && status < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location != null) {
                        if (location.startsWith("/")) {
                            URL base = new URL(currentUrl);
                            location = base.getProtocol() + "://" + base.getHost() + location;
                        }
                        currentUrl = location;
                        continue;
                    }
                } else if (status == 200) {
                    // If we hit a consent page, extract the 'continue' URL
                    if (currentUrl.contains("consent.google.com")) {
                        try {
                            String decoded = java.net.URLDecoder.decode(currentUrl, "UTF-8");
                            if (decoded.contains("continue=")) {
                                currentUrl = decoded.split("continue=")[1].split("&")[0];
                                Log.d(TAG, "Bypassing consent page to: " + currentUrl);
                                continue;
                            }
                        } catch (Exception ignored) {}
                    }
                    
                    // If the shortener returns 200 (splash page), scan for the real URL
                    if (currentUrl.contains("maps.app.goo.gl")) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.contains("url=")) {
                                    Pattern p = Pattern.compile("url=([^&\"']+)");
                                    Matcher m = p.matcher(line);
                                    if (m.find()) {
                                        currentUrl = java.net.URLDecoder.decode(m.group(1), "UTF-8");
                                        Log.d(TAG, "Extracted destination from splash: " + currentUrl);
                                        break;
                                    }
                                }
                            }
                        }
                        continue;
                    }
                }
            } finally {
                conn.disconnect();
            }
            return currentUrl;
        }
        return currentUrl;
    }

    private String extractCoords(String url) {
        if (url == null) return null;
        // Check for coordinates in the URL (both standard and escaped)
        String decodedUrl = url;
        try { decodedUrl = java.net.URLDecoder.decode(url, "UTF-8"); } catch (Exception ignored) {}

        Pattern[] patterns = {
            Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
            Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)"),
            Pattern.compile("place/(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        };

        for (Pattern p : patterns) {
            Matcher m = p.matcher(decodedUrl);
            if (m.find()) return m.group(1) + "," + m.group(2);
        }
        
        // Check for !1d / !2d separately
        if (decodedUrl.contains("!2d") && decodedUrl.contains("!1d")) {
            try {
                String lon = decodedUrl.split("!1d")[1].split("!")[0].split("[&\\?]")[0];
                String lat = decodedUrl.split("!2d")[1].split("!")[0].split("[&\\?]")[0];
                if (isValid(lat, lon)) return lat + "," + lon;
            } catch (Exception ignored) {}
        }
        
        return null;
    }

    private String fetchCoordsFromPage(String urlString) {
        try {
            // Step 1: Extract CID/FID from the URL (handles both direct and consent-wrapped URLs)
            String cidFid = null;
            String decodedUrl = urlString;
            try { decodedUrl = java.net.URLDecoder.decode(urlString, "UTF-8"); } catch (Exception ignored) {}

            if (decodedUrl.contains("!1s0x")) {
                try {
                    cidFid = decodedUrl.split("!1s")[1].split("!")[0].split("[&\\?]")[0];
                    Log.d(TAG, "Extracted CID:FID: " + cidFid);
                } catch (Exception ignored) {}
            }

            // Step 2: Use the Preview API with the CID/FID
            if (cidFid != null) {
                String apiUrl = "https://www.google.com/maps/preview/place?hl=en&pb=!1m2!1s" + cidFid;
                Log.d(TAG, "Calling Preview API: " + apiUrl);
                HttpURLConnection apiConn = (HttpURLConnection) new URL(apiUrl).openConnection();
                apiConn.setConnectTimeout(10000);
                apiConn.setReadTimeout(10000);
                apiConn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
                apiConn.setRequestProperty("Cookie", "SOCS=CAISHAgBEhJnd3NfMm9yZF9tYXBzXzIwMjQwNDA3X1JDMxoBZW4gACIBGgA");

                if (apiConn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(apiConn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            Pattern p = Pattern.compile("\\[null,null,(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)\\]");
                            Matcher m = p.matcher(line);
                            if (m.find()) return m.group(1) + "," + m.group(2);
                        }
                    }
                }
            }

            // Step 3: Scrape if API fails
            Log.d(TAG, "Full page scrape for: " + urlString);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
            conn.setRequestProperty("Cookie", "SOCS=CAISHAgBEhJnd3NfMm9yZF9tYXBzXzIwMjQwNDA3X1JDMxoBZW4gACIBGgA; CONSENT=YES+");

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                    if (content.length() > 800000) break;
                }
            }
            
            String html = content.toString();
            // Try all patterns in the HTML
            Pattern[] htmlPatterns = {
                Pattern.compile("!1x(\\d{8,10})!2x(\\d{8,10})"), // Fixed-point
                Pattern.compile("\\[null,null,(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)\\]"), // JSON
                Pattern.compile("staticmap\\?center=(-?\\d+\\.\\d+)(?:%2C|,)(-?\\d+\\.\\d+)") // Meta
            };

            for (Pattern p : htmlPatterns) {
                Matcher m = p.matcher(html);
                while (m.find()) {
                    String lat, lon;
                    if (m.groupCount() == 2 && p.pattern().contains("!1x")) {
                        lat = String.format("%.7f", Double.parseDouble(m.group(1)) / 10000000.0);
                        lon = String.format("%.7f", Double.parseDouble(m.group(2)) / 10000000.0);
                    } else {
                        lat = m.group(1); lon = m.group(2);
                    }
                    if (isValid(lat, lon)) return lat + "," + lon;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Extraction failed", e);
        }
        return null;
    }

    private boolean isValid(String lat, String lon) {
        if (lat == null || lon == null) return false;
        return !(lat.startsWith("52.199") && lon.startsWith("4.508"));
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}