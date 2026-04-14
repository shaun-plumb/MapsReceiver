package com.coderscollective.mapsreceiver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.net.HttpURLConnection;
import java.net.URL;

public class FileProcessorService_Old extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String sharedText = intent.getStringExtra("shared_text");
            if (sharedText != null) {
                new Thread(() -> {
                    try {
                        System.out.println("Received text: " + sharedText);
                        String sharedURL = null;
                        int startIndex = sharedText.indexOf("https://");
                        if (startIndex != -1) {
                            int endIndex = sharedText.indexOf(" ", startIndex);
                            if (endIndex == -1) {
                                sharedURL = sharedText.substring(startIndex);
                            } else {
                                sharedURL = sharedText.substring(startIndex, endIndex);
                            }
                        }

                        if (sharedURL == null) {
                            System.out.println("No URL found in shared text.");
                            stopSelf();
                            return;
                        }

                        URL url = new URL(sharedURL);

                        // 1. Check redirect with default (Mobile) User-Agent
                        HttpURLConnection ucon = (HttpURLConnection) url.openConnection();
                        ucon.setInstanceFollowRedirects(false);
                        String mobileRedirect = ucon.getHeaderField("Location");
                        System.out.println("Mobile Redirected URL: " + mobileRedirect);

                        // 2. Check redirect with Desktop Chrome User-Agent
                        HttpURLConnection uconDesktop = (HttpURLConnection) url.openConnection();
                        uconDesktop.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                        uconDesktop.setInstanceFollowRedirects(false);
                        String desktopRedirect = uconDesktop.getHeaderField("Location");
                        System.out.println("Desktop Redirected URL: " + desktopRedirect);


                        // TODO: Your text processing logic here
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    stopSelf();
                }).start();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
