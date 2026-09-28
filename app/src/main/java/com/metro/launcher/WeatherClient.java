package com.metro.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;

/** Weather from Open-Meteo (free, no API key needed). */
final class WeatherClient {
    private WeatherClient() {}

    static final class Weather {
        double temp, hi, lo, tHi, tLo;
        int code, tCode;
    }

    static final class Place {
        double lat, lon;
        String name;
    }

    static Weather fetch(double lat, double lon, boolean celsius) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&temperature_unit=%s&timezone=auto&forecast_days=2",
                lat, lon, celsius ? "celsius" : "fahrenheit");
        JSONObject o = new JSONObject(Util.httpGet(url));
        JSONObject cur = o.getJSONObject("current");
        JSONObject d = o.getJSONObject("daily");
        JSONArray max = d.getJSONArray("temperature_2m_max");
        JSONArray min = d.getJSONArray("temperature_2m_min");
        JSONArray codes = d.getJSONArray("weather_code");
        Weather w = new Weather();
        w.temp = cur.getDouble("temperature_2m");
        w.code = cur.getInt("weather_code");
        w.hi = max.getDouble(0);
        w.lo = min.getDouble(0);
        if (max.length() > 1) {
            w.tHi = max.getDouble(1);
            w.tLo = min.getDouble(1);
            w.tCode = codes.getInt(1);
        } else {
            w.tHi = w.hi;
            w.tLo = w.lo;
            w.tCode = codes.getInt(0);
        }
        return w;
    }

    static Place geocode(String name) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name="
                + URLEncoder.encode(name, "UTF-8");
        JSONObject o = new JSONObject(Util.httpGet(url));
        JSONArray r = o.optJSONArray("results");
        if (r == null || r.length() == 0) return null;
        JSONObject first = r.getJSONObject(0);
        Place p = new Place();
        p.lat = first.getDouble("latitude");
        p.lon = first.getDouble("longitude");
        p.name = first.optString("name", name);
        return p;
    }

    /** Plain-English text for a WMO weather code. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorms";
            case 96: case 99: return "Storms, hail";
            default: return "—";
        }
    }
}
