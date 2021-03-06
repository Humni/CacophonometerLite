package nz.org.cacophonoy.cacophonometerlite;


import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;

import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

class Util {
    private static final String LOG_TAG = Util.class.getName();

    private static File homeFile = null;
    private static File recordingFolder = null;
    private static final String DEFAULT_RECORDINGS_FOLDER = "recordings";

    static boolean checkPermissionsForRecording(Context context) {
        Log.d(LOG_TAG, "Checking permissions needed for recording.");
        if (context == null) {
            Log.e(LOG_TAG, "Context was null when checking permissions");
            return false;
        }
        boolean storagePermission =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean microphonePermission =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean locationPermission =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return (storagePermission && microphonePermission && locationPermission);
    }

    static File getHomeFile() {
        // 15/8/16 Tim Hunt - Going to change file storage location to always use internal phone storage rather than rely on sdcard
        // This is because if sdcard is 'dodgy' can get inconsistent results.
        // Need context to get internal storage location, so need to pass this around the code.
        // If I could be sure the homeFile was set before any of the other directories are needed then I
        // wouldn't need to pass context around to the other methods :-(

        if (homeFile == null){
            homeFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/cacophony");
            //https://developer.android.com/reference/android/content/Context.html#getDir(java.lang.String, int)
            // homeFile = context.getDir("cacophony", Context.MODE_PRIVATE); // getDir creates the folder if it doesn't exist, but needs contect

            if (!homeFile.exists() && !homeFile.isDirectory() && !homeFile.mkdirs()){
                System.out.println("error with home file");
                //TODO, exit program safely from here and display error.
            }

        }
        return homeFile;
    }

    static File getRecordingsFolder(){
        if (recordingFolder == null){
            recordingFolder = new File(getHomeFile(), DEFAULT_RECORDINGS_FOLDER);
            if (!recordingFolder.exists() && !recordingFolder.isDirectory() && !recordingFolder.mkdirs()){
                System.out.println("error with recording file");
                //TODO try to fix problem and if cant output error message then exit, maybe send error to server.
            }
        }
        return recordingFolder;
    }

    public static String getDeviceID(String webToken) throws Exception {
        String webTokenBody =  Util.decoded(webToken);
        JSONObject jObject = new JSONObject(webTokenBody);
       return jObject.getString("id");
    }

    public static String decoded(String JWTEncoded) throws Exception {
        // http://stackoverflow.com/questions/37695877/how-can-i-decode-jwt-token-in-android#38751017
        String webTokenBody = null;
        try {
            String[] split = JWTEncoded.split("\\.");
            Log.d("JWT_DECODED", "Header: " + getJson(split[0]));

           // Log.d("JWT_DECODED", "Body: " + getJson(split[1]));
            webTokenBody = getJson(split[1]);
        } catch (UnsupportedEncodingException e) {
            //Error
        }
        return webTokenBody;
    }

    private static String getJson(String strEncoded) throws UnsupportedEncodingException{
        byte[] decodedBytes = Base64.decode(strEncoded, Base64.URL_SAFE);
        return new String(decodedBytes, "UTF-8");
    }

    public static float getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.getApplicationContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float) scale;
        return batteryPct;
    }

    public static String getBatteryStatus(Context context) {
        // https://developer.android.com/training/monitoring-device-state/battery-monitoring.html
        // http://stackoverflow.com/questions/24934260/intentreceiver-components-are-not-allowed-to-register-to-receive-intents-when
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.getApplicationContext().registerReceiver(null, ifilter);
        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        String batteryStatusToReturn;
        switch (status){
            case BatteryManager.BATTERY_STATUS_CHARGING: batteryStatusToReturn = "CHARGING";
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: batteryStatusToReturn = "DISCHARGING";
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: batteryStatusToReturn = "NOT_CHARGING";
                break;
            case BatteryManager.BATTERY_STATUS_FULL: batteryStatusToReturn = "FULL";
                break;
            case BatteryManager.BATTERY_STATUS_UNKNOWN: batteryStatusToReturn = "UNKNOWN";
                break;
            default: batteryStatusToReturn = Integer.toString(status);
        }
        return batteryStatusToReturn;


    }

    /**
     * Gets the state of Airplane Mode.
     *http://stackoverflow.com/questions/4319212/how-can-one-detect-airplane-mode-on-android
     * @param context
     * @return true if enabled.
     */
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isAirplaneModeOn(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    public static void enableAirplaneMode(Context context){
        //http://stackoverflow.com/posts/5533943/edit
        Settings.System.putInt(
                context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 1);

// Post an intent to reload
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", true);
        context.sendBroadcast(intent);
    }

    public static void disableAirplaneMode(Context context){
        //http://stackoverflow.com/posts/5533943/edit
        Settings.System.putInt(
                context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0);

// Post an intent to reload
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", false);
        context.sendBroadcast(intent);
    }
    public static boolean isSimPresent(Context context){
        // https://sites.google.com/site/androidhowto/how-to-1/check-if-sim-card-exists-in-the-phone
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);  //gets the current TelephonyManager
        if (tm.getSimState() == TelephonyManager.SIM_STATE_ABSENT){
            //Toast.makeText(context, "SAVE POWER - Enable Airplane/Flight mode", Toast.LENGTH_LONG).show();
            return false;
        } else {
           // System.out.println("The phone has a sim card");
            return true;
        }
    }

    /**
     * Returns the sunrise time for the current device location
     * @param context - for getting the location
     * @return Calendar time of the sunrise
     */
    public static Calendar getSunrise(Context context){
        Prefs prefs = new Prefs(context);
        String lat = Double.toString(prefs.getLatitude());
        String lon = Double.toString(prefs.getLongitude());

        Location location = new Location(lat, lon);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, "Pacific/Auckland");
        Calendar officialSunrise = calculator.getOfficialSunriseCalendarForDate(Calendar.getInstance());

        Log.d("DEBUG: ", "Sunrise time is: " + officialSunrise);
        return officialSunrise;
    }

    /**
     * Returns the sunset time for the current device location
     * @param context - for getting the location
     * @return Calendar time of the sunset
     */
    public static Calendar getSunset(Context context){
        Prefs prefs = new Prefs(context);
        String lat = Double.toString(prefs.getLatitude());
        String lon = Double.toString(prefs.getLongitude());

        Location location = new Location(lat, lon);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, "Pacific/Auckland");
        Calendar officialSunset = calculator.getOfficialSunsetCalendarForDate(Calendar.getInstance());

        Log.d("DEBUG: ", "Sunset time is: " + officialSunset);
        return officialSunset;
    }

}
