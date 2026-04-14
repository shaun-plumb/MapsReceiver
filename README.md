![](app/src/main/ic_launcher4-playstore.png)
# MapsReceiver

MapsReceiver is a lightweight Android utility designed to bridge the gap between Google Maps and Garmin devices. It intercepts shared locations or routes from Google Maps, processes them, and sends them directly to the Garmin Explore / Garmin Connect app for navigation.

## Background

I started this project because the Garmin (anything but smart) Smartphone link app stopped parsing shared points from Google Maps. 
Obviously Google had changed the format of their URLs and Garmin weren't interested in supporting the app any more. 
To make matters worse their new apps don't support my GPS.
Since this was a selling point of the GPS I'd bought only a few years previously I was understandably annoyed at this 
lack of support. Some expletives later and I decided to jump into doing it myself.

I worked out that Google maps URLs contain point and route information in a string representation of 
a protobuf object, see: [Protocol Buffers](https://en.wikipedia.org/wiki/Protocol_Buffers). But, without
a definition of the data structure involved, it was not a simple task to extract the values. I did get 
some way forward with a hacked together Java library that kind of gave me a structure that sort of worked, at least 
to the point where I could export a GPX file that I could drop on my GPS and use.

Then I realised that the mobile and desktop versions of Google Maps use different URL structures ... time for some more colourful metaphors ...

Some time later I fired up Android Studio and thought I'd give it another go. I hadn't touched Android development for about 10 years, and it's 
frankly taken that long for the rash to clear up, so I thought I could do with some help and fired up the included Gemini AI agent. 
I threw some ideas at it, told it what I wanted to do, and let it get on with it. This is the result - it took a few iterations, 
some more swearing, sacrificing the odd goat or two, but it did eventually come up with something fairly robust - 
it certainly works on my phone (Samsung S24) and actually has better functionality than the original in that it can share a route with waypoints.

This is the basic flow - you need to have Smartphone link installed, and a GPS unit to send to. 
This app is headless, so there's no main page, but once installed it will appear in your 'share sheet', with the above logo,
whenever you hit the share link on a place or route in Google Maps.

```text
          Google Maps
               |
               v
    Share with MapsReceiver
               |
               v
    Garmin Smartphone Link
               |
       ________|________
      |                 |
      v                 v
  [ Place ]         [ Route ]
      |                 |
      v                 v
  Map Dialog      "Share File"
 (Send button)   (Send to Zumo)
      |                 |
      v                 v
  GPS Point       GPS Route/Trip
 (Go dialog)     (Trip Planner)
```

This works with my GPS, a Zumo 396, I have no idea whether any other Garmin models will support this, but you're welcome to try.

You're free to use this in any way you want, but I can't offer any support. If you find interesting ways of improving it, I'd like to hear them.

You need a Google Maps API key (with Places API enabled) to use this app [Google APIs](https://mapsplatform.google.com/lp/maps-apis/). 
It is required to extract the coordinates for a single place as the shared information only contains a 
reference that needs to be looked up. The route shares don't need this (yet).

If you keep to under 10,000 or so requests, this usage is free. So don't go mad ... :) 

## Features

- **Share Places**: Extract coordinates from Google Maps "Share" links and send them to Garmin as a waypoint.
- **Share Routes**: Convert Google Maps multi-point routes into GPX files and open them directly in the Garmin app.
- **Robust Link Handling**:
    - Handles `goo.gl` and `maps.app.goo.gl` short links.
    - Advanced redirection logic with multi-strategy retries (Mobile/Desktop User-Agents) to avoid 404 errors.
    - Automatic consent page bypass.
- **Places API Integration**: Uses Google Places API to resolve coordinates for locations when they aren't present in the URL.
- **GPX Generation**: Automatically generates standards-compliant GPX files for routes, including support for "Current Position" as a start point.

## How it Works

1. **Intercept**: The app registers as a handler for `text/plain` sharing intents.
2. **Process**:
    - The `FileProcessorService` extracts the URL from the shared text.
    - It follows redirects to find the final Google Maps URL.
    - It parses the URL or uses the Places API to find latitude and longitude.
    - For routes, it decodes the Google Maps internal "pb" string to extract waypoints.
3. **Dispatch**: The processed data (coordinates or GPX file) is sent to the Garmin app (`com.garmin.android.apps.phonelink`) using a specialized Intent.

## Setup

### Prerequisites
- Android Studio to build the app and publish to your phone.
- A Google Maps API Key (with Places API enabled).
- A Garmin device and the Garmin Smartphone Link app installed on your phone.

This application will only share to the Garmin Smartphone Link app and won't function if it is not installed.

### Configuration
1. Create a `local.properties` file in the project root if it doesn't exist.
2. Add your Google Maps API key:
   ```properties
   MAPS_API_KEY=your_api_key_here
   ```
3. Build and install the app on your Android device.

### Permissions
On Android 13+, the app requires `POST_NOTIFICATIONS` permission to show status messages (Toasts) while processing in the background. You can grant this via:
```bash
adb shell pm grant com.coderscollective.mapsreceiver android.permission.POST_NOTIFICATIONS
```

You may also need to manually set notifications to 'on' for the MapsReceiver app in your phone's settings.

## Usage
1. Open Google Maps.
2. Search for a place or plan a route.
3. Tap **Share**.
4. Select **MapsReceiver** from the list of apps (you may have to expand the list and look for it if it's the first time).
5. The Garmin app will automatically open with your location or route prepared for sending to the GPS device.

## Technical Details
- **IPv4 Preference**: The app forces IPv4 usage to resolve `ConnectException` issues found on some network configurations when accessing Google services.
- **Unsigned CID**: For Places API calls, the app correctly handles 64-bit unsigned CIDs to prevent "Invalid Request" errors.
- **UA Flipping**: To reliably resolve Google's short links, the app retries requests with different User-Agents if a 404 is encountered.
- **Silent Waypoints**: The logic also supports silent 'shaping' points that behave like waypoints, but aren't announced when you reach them. Unfortunately you can only create these in the desktop browser version of Google Maps, not on mobile, but if it's ever supported, these will work.
