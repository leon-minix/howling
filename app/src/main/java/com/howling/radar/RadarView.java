package com.howling.radar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.wifi.ScanResult;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadarView extends View {
    private Paint paint = new Paint();
    private Paint sweepPaint = new Paint();
    private Paint textPaint = new Paint();
    
    private Paint boxPaint = new Paint(); 
    private Paint popupTextPaint = new Paint(); 
    
    private float sweepAngle = 0;
    private float azimuth = 0;
    private List<ScanResult> wifiResults = new ArrayList<>();
    
    private SweepGradient sweepGradient;
    
    private SoundPool soundPool;
    private int pingSoundId;
    private boolean soundLoaded = false;
    private Set<String> pingedDevices = new HashSet<>();

    // Touch Detection
    private Map<String, float[]> targetPositions = new HashMap<>();
    private ScanResult selectedWifi = null;
    
    // Popup ပေါ်မည့်နေရာ (Touch လုပ်လိုက်သည့်နေရာ)
    private float popupX = 0;
    private float popupY = 0;

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        sweepPaint.setAntiAlias(true);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Popup Paint
        boxPaint.setAntiAlias(true);
        popupTextPaint.setAntiAlias(true);
        popupTextPaint.setTypeface(Typeface.MONOSPACE); 

        int[] colors = {Color.TRANSPARENT, Color.argb(200, 0, 255, 0)}; 
        sweepGradient = new SweepGradient(0, 0, colors, new float[]{0.75f, 1.0f});
        
        initSound(context);
    }

    private void initSound(Context context) {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attrs).build();
            pingSoundId = soundPool.load(context, R.raw.sonar_ping, 1);
            soundPool.setOnLoadCompleteListener((soundPool1, sampleId, status) -> soundLoaded = true);
        } catch (Exception e) {}
    }

    public void updateBlips(List<ScanResult> results) {
        this.wifiResults = results;
        if (selectedWifi != null) {
            boolean found = false;
            for (ScanResult res : results) {
                if (res.BSSID.equals(selectedWifi.BSSID)) {
                    selectedWifi = res;
                    found = true;
                    break;
                }
            }
            if (!found) selectedWifi = null; 
        }
        invalidate();
    }

    public void setAzimuth(float azimuth) {
        this.azimuth = azimuth;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            // 1. Check Exit Button (Popup ပွင့်နေမှ စစ်မယ်)
            if (selectedWifi != null) {
                float boxW = 550;
                float boxH = 750;
                
                // Popup ဘယ်နားမှာ ပေါ်နေလဲ ပြန်တွက်ရမယ် (Smart Position Logic အတိုင်း)
                float drawX = popupX;
                float drawY = popupY;

                // Screen Boundary Check (Draw Logic နဲ့ တူအောင်ညှိ)
                if (drawX + boxW > getWidth()) drawX = getWidth() - boxW - 20;
                if (drawY + boxH > getHeight()) drawY = getHeight() - boxH - 20;
                if (drawX < 20) drawX = 20;
                if (drawY < 20) drawY = 20;
                
                // Exit Button Area (ညာဘက်အပေါ်ထောင့်)
                if (x > drawX + boxW - 80 && x < drawX + boxW && y > drawY && y < drawY + 80) {
                    selectedWifi = null; // ပိတ်မယ်
                    invalidate();
                    return true;
                }
            }

            // 2. Check Radar Blip Click
            String clickedBSSID = null;
            for (Map.Entry<String, float[]> entry : targetPositions.entrySet()) {
                float tx = entry.getValue()[0];
                float ty = entry.getValue()[1];
                if (Math.hypot(x - tx, y - ty) < 60) { // Touch Radius
                    clickedBSSID = entry.getKey();
                    
                    // နှိပ်လိုက်တဲ့နေရာကို မှတ်ထားမယ် (Popup ပြဖို့)
                    popupX = x;
                    popupY = y;
                    break;
                }
            }

            if (clickedBSSID != null) {
                for (ScanResult res : wifiResults) {
                    if (res.BSSID.equals(clickedBSSID)) {
                        selectedWifi = res;
                        invalidate();
                        break;
                    }
                }
            } 
            
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int radius = Math.min(getWidth(), getHeight()) / 2 - 140;

        targetPositions.clear(); 

        // --- Static Background ---
        drawDigitalHUD(canvas);
        
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GREEN);
        
        paint.setAlpha(120);
        paint.setStrokeWidth(4);
        canvas.drawCircle(centerX, centerY, radius + 15, paint);
        paint.setStrokeWidth(1);
        canvas.drawCircle(centerX, centerY, radius + 25, paint);

        paint.setAlpha(60);
        canvas.drawCircle(centerX, centerY, radius * 0.66f, paint);
        canvas.drawCircle(centerX, centerY, radius * 0.33f, paint);

        // --- Rotating Elements ---
        canvas.save();
        canvas.rotate(-azimuth, centerX, centerY);
        
        for (int i = 0; i < 360; i += 5) {
            float startR = radius + 15;
            float endR = (i % 90 == 0) ? radius - 20 : (i % 10 == 0 ? radius - 10 : radius + 5);
            
            float angleRad = (float) Math.toRadians(i);
            float cos = (float) Math.cos(angleRad);
            float sin = (float) Math.sin(angleRad);

            float x1 = centerX + cos * startR;
            float y1 = centerY + sin * startR;
            float x2 = centerX + cos * endR;
            float y2 = centerY + sin * endR;
            
            paint.setAlpha(i % 90 == 0 ? 200 : 80);
            paint.setStrokeWidth(i % 90 == 0 ? 3 : 1);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }

        paint.setAlpha(50);
        paint.setStrokeWidth(2);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paint);
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paint);

        textPaint.setColor(Color.GREEN);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(40);
        textPaint.setAlpha(255);
        
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("N", centerX, centerY - radius - 45, textPaint);
        canvas.drawText("S", centerX, centerY + radius + 75, textPaint);
        
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("E", centerX + radius + 45, centerY + 15, textPaint);
        
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("W", centerX - radius - 45, centerY + 15, textPaint);

        // --- WiFi Targets ---
        for (ScanResult result : wifiResults) {
            int macHash = result.BSSID.hashCode();
            float targetAngle = Math.abs(macHash % 360);
            float visualAngle = (targetAngle - azimuth + 360) % 360; 
            float angleDiff = (sweepAngle - visualAngle + 360) % 360;
            
            if (soundLoaded && angleDiff >= 0 && angleDiff < 4) {
                if (!pingedDevices.contains(result.BSSID)) {
                    soundPool.play(pingSoundId, 0.5f, 0.5f, 1, 0, 1.0f);
                    pingedDevices.add(result.BSSID);
                }
            } else if (angleDiff > 10) {
                pingedDevices.remove(result.BSSID);
            }
            
            int alpha = (angleDiff < 310) ? (int) (255 * (1.0f - (angleDiff / 310f))) : 0;

            if (alpha > 15) {
                float strength = Math.min(1.0f, Math.max(0.1f, (100f + result.level) / 70f));
                float dist = (1.0f - strength) * radius; 
                
                float rad = (float) Math.toRadians(targetAngle);
                float x = (float) (centerX + Math.cos(rad) * dist);
                float y = (float) (centerY + Math.sin(rad) * dist);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(result.level > -65 ? Color.GREEN : Color.RED);
                paint.setAlpha(alpha);
                
                float size = (angleDiff < 20) ? 14 : 10; 
                canvas.drawRect(x - size, y - size, x + size, y + size, paint);
                
                double screenRad = Math.toRadians(targetAngle - azimuth);
                float screenX = (float) (centerX + Math.cos(screenRad) * dist);
                float screenY = (float) (centerY + Math.sin(screenRad) * dist);
                targetPositions.put(result.BSSID, new float[]{screenX, screenY});

                textPaint.setColor(Color.WHITE);
                textPaint.setAlpha(alpha);
                textPaint.setTextSize(24);
                textPaint.setTextAlign(Paint.Align.LEFT);
                String ssidName = result.SSID.isEmpty() ? "HIDDEN" : (result.SSID.length() > 12 ? result.SSID.substring(0, 12) + "..." : result.SSID);
                String displayText = ssidName + " [" + (int)(dist/10) + "m]";
                canvas.drawText(displayText, x + 25, y - 5, textPaint);
                
                textPaint.setColor(Color.CYAN);
                textPaint.setTextSize(18);
                canvas.drawText("SEC: " + getSecurityType(result.capabilities), x + 25, y + 20, textPaint);
            }
        }
        canvas.restore();

        // --- Sweep Line ---
        sweepPaint.setShader(sweepGradient); 
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.rotate(sweepAngle);
        canvas.drawCircle(0, 0, radius, sweepPaint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GREEN);
        paint.setAlpha(200);
        paint.setStrokeWidth(5);
        canvas.drawLine(0, 0, radius, 0, paint);
        canvas.restore();
        
        // --- ၅။ Popup Box (Smart Positioning) ---
        if (selectedWifi != null) {
            // Arguments ထည့်စရာမလိုတော့ဘူး၊ popupX/Y ကိုသုံးမယ်
            drawVerticalPopup(canvas);
        }

        sweepAngle += 1.0f; 
        if (sweepAngle >= 360) sweepAngle = 0;
        invalidate();
    }
    
    // ဒီ Function မှာ Logic အသစ် ပြင်ထားပါတယ်
    private void drawVerticalPopup(Canvas canvas) {
        float boxW = 550;
        float boxH = 750;
        
        // စစချင်း နှိပ်လိုက်တဲ့နေရာကို ယူမယ်
        float startX = popupX;
        float startY = popupY;

        // --- SMART POSITIONING LOGIC ---
        // ညာဘက်အစွန် ကပ်နေရင် ဘယ်ဘက်ကို ရွှေ့မယ်
        if (startX + boxW > getWidth()) {
            startX = getWidth() - boxW - 20; // Screen အဆုံးကနေ 20px ခွာမယ်
        }
        
        // အောက်ဘက်အစွန် ကပ်နေရင် အပေါ်ကို ရွှေ့မယ်
        if (startY + boxH > getHeight()) {
            startY = getHeight() - boxH - 20; 
        }
        
        // ဘယ်ဘက်နဲ့ အပေါ်ဘက် လွတ်နေရင်လည်း ပြန်ထိန်းမယ်
        if (startX < 20) startX = 20;
        if (startY < 20) startY = 20;

        float left = startX;
        float top = startY;
        float right = left + boxW;
        float bottom = top + boxH;
        float cornerRadius = 30f;

        // 1. Black Background
        boxPaint.setStyle(Paint.Style.FILL);
        boxPaint.setColor(Color.BLACK);
        boxPaint.setAlpha(245); 
        RectF rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint);

        // 2. Red Border
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(Color.RED);
        boxPaint.setStrokeWidth(3); 
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint);

        // 3. Exit Button [X]
        float xCenterX = right - 35; 
        float xCenterY = top + 35;
        float xRadius = 18;

        boxPaint.setStyle(Paint.Style.FILL);
        boxPaint.setColor(Color.RED);
        canvas.drawCircle(xCenterX, xCenterY, xRadius, boxPaint);
        
        popupTextPaint.setColor(Color.WHITE);
        popupTextPaint.setTextSize(22);
        popupTextPaint.setFakeBoldText(true);
        popupTextPaint.setTextAlign(Paint.Align.CENTER);
        float textYOffset = (popupTextPaint.descent() + popupTextPaint.ascent()) / 2;
        canvas.drawText("X", xCenterX, xCenterY - textYOffset, popupTextPaint);

        // 4. Data Rows
        float textX = left + 30;
        float currentY = top + 60;
        float gap = 48; 

        popupTextPaint.setTextAlign(Paint.Align.LEFT);
        popupTextPaint.setTextSize(26); 
        popupTextPaint.setFakeBoldText(true);
        popupTextPaint.setColor(Color.GREEN);
        
        // Header
        String ssid = selectedWifi.SSID.isEmpty() ? "<HIDDEN>" : selectedWifi.SSID;
        if(ssid.length() > 18) ssid = ssid.substring(0, 18) + "...";
        canvas.drawText("TARGET: " + ssid, textX, currentY, popupTextPaint);
        
        // Line
        boxPaint.setColor(Color.DKGRAY);
        boxPaint.setStrokeWidth(2);
        canvas.drawLine(left + 20, currentY + 15, right - 20, currentY + 15, boxPaint);
        
        currentY += 20;
        popupTextPaint.setTextSize(22); 
        popupTextPaint.setFakeBoldText(false);

        // --- DATA POINTS ---
        drawRow(canvas, "BSSID", selectedWifi.BSSID, textX, currentY += gap, Color.WHITE);
        
        int rssiColor = selectedWifi.level > -60 ? Color.GREEN : (selectedWifi.level > -80 ? Color.YELLOW : Color.RED);
        drawRow(canvas, "SIGNAL", selectedWifi.level + " dBm", textX, currentY += gap, rssiColor);
        
        drawRow(canvas, "FREQ", selectedWifi.frequency + " MHz", textX, currentY += gap, Color.CYAN);
        drawRow(canvas, "CHAN", "CH " + getChannel(selectedWifi.frequency), textX, currentY += gap, Color.CYAN);
        
        String sec = getSecurityType(selectedWifi.capabilities);
        drawRow(canvas, "SEC", sec, textX, currentY += gap, sec.contains("OPEN") ? Color.RED : Color.WHITE);
        
        String vendor = "Unknown";
        try { vendor = MacVendorHelper.getVendor(selectedWifi.BSSID); } catch (Exception e) {}
        drawRow(canvas, "VENDOR", vendor, textX, currentY += gap, Color.MAGENTA);
        
        String width = "20 MHz";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            width = (selectedWifi.channelWidth == ScanResult.CHANNEL_WIDTH_20MHZ) ? "20 MHz" : "40+ MHz";
        }
        drawRow(canvas, "WIDTH", width, textX, currentY += gap, Color.LTGRAY);
        drawRow(canvas, "DIST", getDist(selectedWifi.level) + "m", textX, currentY += gap, Color.YELLOW);
        drawRow(canvas, "WPS", selectedWifi.capabilities.contains("WPS") ? "Yes" : "No", textX, currentY += gap, Color.LTGRAY);
        drawRow(canvas, "CLIENTS", "0 (Idle)", textX, currentY += gap, Color.DKGRAY);
       // drawRow(canvas, "LAST_SEEN", "Now", textX, currentY += gap, Color.GREEN);
    }

    private void drawRow(Canvas canvas, String label, String value, float x, float y, int valColor) {
        popupTextPaint.setColor(Color.GRAY);
        canvas.drawText(label + ": ", x, y, popupTextPaint);
        float w = popupTextPaint.measureText(label + ": ");
        popupTextPaint.setColor(valColor);
        canvas.drawText(value, x + w, y, popupTextPaint);
    }

    private void drawDigitalHUD(Canvas canvas) {
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(24);
        textPaint.setAlpha(180);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("SYSTEM: ONLINE", 40, 60, textPaint);
        
        textPaint.setTextAlign(Paint.Align.RIGHT);
        
        // 16-Point Compass
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = Math.round(azimuth / 22.5f) % 16;
        if (index < 0) index += 16;
        String dirName = directions[index]; 
        
        canvas.drawText("AZIMUTH: " + (int)azimuth + "° " + dirName, getWidth() - 40, 60, textPaint);
        canvas.drawText("TARGETS: " + wifiResults.size(), getWidth() - 40, 95, textPaint);
        /*paint.setColor(Color.GREEN);
        paint.setAlpha(100);
        paint.setStrokeWidth(2);
        canvas.drawLine(40, getHeight() - 60, 150, getHeight() - 60, paint);
        canvas.drawLine(getWidth() - 150, getHeight() - 60, getWidth() - 40, getHeight() - 60, paint);*/
    }

    private String getSecurityType(String caps) {
        if (caps.contains("WPA3")) return "WPA3";
        if (caps.contains("WPA2")) return "WPA2";
        if (caps.contains("WPA")) return "WPA";
        if (caps.contains("WEP")) return "WEP";
        return "OPEN";
    }
    
    private int getChannel(int freq) {
        if (freq >= 2412 && freq <= 2484) return (freq - 2412) / 5 + 1;
        if (freq >= 5170 && freq <= 5825) return (freq - 5170) / 5 + 34;
        return 0;
    }
    
    private String getDist(int level) {
         double exp = (27.55 - (20 * Math.log10(2400)) + Math.abs(level)) / 20.0;
         return String.format("%.1f", Math.pow(10.0, exp));
    }
}