package com.howling.radar;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MacVendorHelper {
    private static final Map<String, String> macDb = new HashMap<>();
    private static final String TAG = "RadarVendor";

    public static void loadDatabase(Context context) {
        if (!macDb.isEmpty()) return;

        try {
            // Assets ထဲက oui.db ကို ဖတ်မယ်
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("oui.db"))
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        // Key ကို UpperCase ပြောင်းပြီး သိမ်းမယ်
                        macDb.put(parts[0].trim().toUpperCase(), parts[1].trim());
                    }
                }
            }
            reader.close();
            Log.d(TAG, "Database successfully loaded! Total: " + macDb.size());
        } catch (Exception e) {
            Log.e(TAG, "Critical error loading oui.db: " + e.getMessage());
        }
    }

    public static String getVendor(String bssid) {
        if (bssid == null || bssid.length() < 8) return "Unknown";

        // BSSID: "CC:29:BD:66:D3:7E" -> "CC29BD"
        String cleanMac = bssid.replace(":", "").toUpperCase();
        
        if (cleanMac.length() >= 6) {
            String prefix = cleanMac.substring(0, 6);
            
            // Log ထုတ်ပြီး စစ်ကြည့်မယ် (Terminal မှာ 'adb logcat -s RadarVendor' နဲ့ကြည့်ပါ)
            String found = macDb.get(prefix);
            if (found != null) {
                return found;
            } else {
                Log.d(TAG, "Not found in DB: " + prefix);
            }
        }
        return "Unknown";
    }
}