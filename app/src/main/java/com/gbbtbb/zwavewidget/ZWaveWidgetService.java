package com.gbbtbb.zwavewidget;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ZWaveWidgetService extends IntentService {

    public ZWaveWidgetService() {
        super(ZWaveWidgetService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        final String action = intent.getAction();

        // Create a RemoteViews to include any UI changes
        RemoteViews rv = new RemoteViews(this.getPackageName(), R.layout.zwavewidget);

        /* XML config data structure for each device
        First the device, instance, and command class number used for Get and Set commands

        0 = devId_setget
        1 = instanceId_setget
        2 = commandclass_setget

        Then the device, instance, and command class number used to detect changed when parsing incremental data updates.
        In 99% of the cases, those are identical to 0,1,2
        But for at least one plug device, for some reason when the device is manually switched, the change is detected/notified with a different dev/instance/class.

        3 = devId_notified
        4 = instanceId_notified
        5 = commandclass_notified

        Then the android Id for the associated ImageView and TextView
        6 = imageViewId
        7 = textViewId

        Finally the base name for the image resource to be used to show the on/off state
        8 = icontype
        */

        if (ZWaveWidgetProvider.INITIALIZE_ACTION.equals(action)) {

            Log.i("ZWaveWidgetService", "onHandleIntent INITIALIZE_ACTION");

            // retrieve server-side timestamp, since this will be used in incremental updates
            getServerTime();

            String[] deviceList = getResources().getStringArray(R.array.deviceList);

            // now get data for every device
            for (String d : deviceList) {
                int arrayId = this.getResources().getIdentifier(d, "array", this.getPackageName());
                String[] temp = getResources().getStringArray(arrayId);
                forceRefreshDevice(rv, temp[0], temp[1], temp[2], temp[6], temp[7], temp[8], d);
            }

        } else if (ZWaveWidgetProvider.REFRESH_ACTION.equals(action)) {

            long lastRefreshTime = intent.getExtras().getLong(ZWaveWidgetProvider.LATEST_REFRESH_EXTRA);
            //Log.i("ZWaveWidgetService", "onHandleIntent REFRESH_ACTION date=" + Long.toString(lastRefreshTime));

            // Get all state changes since last update from the z-way server
            JSONObject jdata = getIncrementalUpdate(lastRefreshTime);

            // Parse the list of all declared devices, and figure out if a UI update is required
            String[] deviceList = getResources().getStringArray(R.array.deviceList);
            for (String d : deviceList) {
                int arrayId = this.getResources().getIdentifier(d, "array", this.getPackageName());
                String[] temp = getResources().getStringArray(arrayId);
                refreshDevice(jdata, rv, temp[0], temp[1], temp[2],temp[3], temp[4], temp[5], temp[6], temp[7], temp[8], d);
            }

        }  else if (ZWaveWidgetProvider.TOGGLE_ACTION.equals(action)) {

            String deviceName = intent.getExtras().getString(ZWaveWidgetProvider.DEVICE_NAME_EXTRA);
            Log.i("ZWaveWidgetService", "onHandleIntent TOGGLE_ACTION, toggle " + deviceName);
            int arrayId = this.getResources().getIdentifier(deviceName, "array", this.getPackageName());
            String[] temp = getResources().getStringArray(arrayId);
            toggleDevice(rv, temp[0], temp[1], temp[2], temp[6], temp[7], temp[8], deviceName);
        }

        // Finally, refresh the widget with these new UI states.
        ComponentName me = new ComponentName(this, ZWaveWidgetProvider.class);
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        mgr.updateAppWidget(me, rv);
        Log.i("ZWaveWidgetService", "refreshing widget UI");

    }

    private JSONObject getIncrementalUpdate(long timestamp){
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base) + "/ZWaveAPI/Data/%d", timestamp);
        String result = httpRequest(query_Data);

        String updateTime ="";
        JSONObject jdata = null;

        // Parse the received JSON data
        try {
            jdata = new JSONObject(result);
            updateTime = jdata.getString("updateTime");

            // Notify provider of data refresh time
            final Intent storeTimeIntent = new Intent(this, ZWaveWidgetProvider.class);
            storeTimeIntent.setAction(ZWaveWidgetProvider.STORE_REFRESH_TIME_ACTION);
            storeTimeIntent.putExtra(ZWaveWidgetProvider.STORE_REFRESH_TIME_EXTRA, Long.valueOf(updateTime));
            final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, storeTimeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            try {
                donePendingIntent.send();
            }
            catch (PendingIntent.CanceledException ce) {
                Log.i("ZWaveWidgetService", "getIncrementalUpdate: Exception: " + ce.toString());
            }

        } catch(JSONException e){
            Log.e("ZWaveWidgetService", "getIncrementalUpdate: Error parsing data " + e.toString());
        }

        return jdata;
    }

    private void refreshDevice(JSONObject jdata, RemoteViews rv, String devId_setget, String devInstanceId_setget, String devCommandClass_setget, String devId_notified, String devInstanceId_notified, String devCommandClass_notified, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Build the string to look for in the JSON data
        String key_main = String.format("devices.%s.instances.%s.commandClasses.%s.data.level", devId_setget, devInstanceId_setget, devCommandClass_setget);
        String key_alt = String.format("devices.%s.instances.%s.commandClasses.%s.data.level", devId_notified, devInstanceId_notified, devCommandClass_notified);

        String level_string="";
        long updateTime;
        long invalidateTime;
        int level=0;
        JSONObject j_main = null;
        JSONObject j_alt = null;

        // Parse the JSON data and check if this particular device changed state
        // Some device state changes may be notified by two different ways, depending on how their change was triggered
        // so look for both the main key string and the alternate key string in the JSON data
        try {
            j_main = jdata.getJSONObject(key_main);
        } catch(JSONException e){
        }

        try {
            j_alt = jdata.getJSONObject(key_alt);
        } catch(JSONException e){
        }

        JSONObject j = (j_main !=null) ? j_main: (j_alt != null) ? j_alt: null;
        if (j != null) {
            try {
                level_string = j.getString("value");
                updateTime = Long.valueOf(j.getString("updateTime"));
                invalidateTime = Long.valueOf(j.getString("invalidateTime"));

                // This checks is important, to filter out stale data notification
                // i.e. notification of a device state gathered *just* before it was manually changed from here
                // If not filtered, the next refresh will have the good data, but the UI will flash to the wrong state, not nice.
                if (updateTime > invalidateTime) {

                    if ("true".equals(level_string))
                        level = 1;
                    else if ("false".equals(level_string))
                        level = 0;
                    else
                        level = Integer.valueOf(j.getString("value"));

                    Log.i("ZWaveWidgetService", "found change for " + key_main + " or "+ key_alt +" , level=" + Integer.toString(level));

                    // Refresh icon
                    updateImageView(rv, imgViewId, icontype_base, level);
                }
                else {
                    Log.i("ZWaveWidgetService", "STALE update for " + key_main + ", discarding ");
                }
            } catch(JSONException e){
                Log.e("ZWaveWidgetService", "refreshDevice: Error parsing data " + e.toString());
            }
        } else {
            //Log.i("ZWaveWidgetService", "No change detected for " + key_main + "or "+ key_alt);
        }
    }

    private void toggleDevice(RemoteViews rv, String devId, String devInstanceId, String devCommandClass, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Get current state of this device
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base)+"/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].data.level", devId, devInstanceId, devCommandClass);
        String result = httpRequest(query_Data);

        String level_string="";
        int level=0;

        // Parse the received JSON data
        if (!"".equals(result)) {
            try {
                JSONObject jdata = new JSONObject(result);
                level_string = jdata.getString("value");

                if ("true".equals(level_string))
                    level = 1;
                else if ("false".equals(level_string))
                    level = 0;
                else
                    level = Integer.valueOf(jdata.getString("value"));

                Log.i("ZWaveWidgetService", "updateSwitch (" + name + "): level=" + Integer.toString(level));

            } catch (JSONException e) {
                Log.e("ZWaveWidgetService", "toggleDevice: Error parsing data " + e.toString());
            }

            // toggle current state
            if (level == 0)
                level = 255; // 255 works even if max value is actually 1 or 99 or whatever.
            else
                level = 0;

            // Refresh icon
            updateImageView(rv, imgViewId, icontype_base, level);

            // Set new level on device
            String query_SetLevel = String.format(this.getResources().getString(R.string.zwaveserver_base) + "/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].Set(%d)", devId, devInstanceId, devCommandClass, level);
            httpRequest(query_SetLevel);
        }
    }

    private void forceRefreshDevice(RemoteViews rv, String devId, String devInstanceId, String devCommandClass, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Perform an explicit GET command on the device, to be sure that device status is fresh
        String query_Get = String.format(this.getResources().getString(R.string.zwaveserver_base)+"/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].Get()", devId, devInstanceId, devCommandClass);
        httpRequest(query_Get);

        // Then get the actual data
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base) + "/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].data.level", devId, devInstanceId, devCommandClass);
        String result = httpRequest(query_Data);

        String level_string="";
        int level=0;

        // Parse the received JSON data
        try {
            JSONObject jdata = new JSONObject(result);
            level_string = jdata.getString("value");

            if ("true".equals(level_string))
                level = 1;
            else if ("false".equals(level_string))
                level = 0;
            else
                level = Integer.valueOf(jdata.getString("value"));

            Log.i("ZWaveWidgetService", "updateSwitch ("+name+"): level=" + Integer.toString(level));

        } catch(JSONException e){
            Log.e("ZWaveWidgetService", "forceRefreshDevice: Error parsing data "+e.toString());
        }

        updateImageView(rv, imgViewId, icontype_base, level);

        int textViewId = this.getResources().getIdentifier(txtViewId, "id", this.getPackageName());
        rv.setTextViewText(textViewId, name);
    }

    private void updateImageView(RemoteViews rv, String imgViewId, String icontype_base, int level) {
        int imageViewId = this.getResources().getIdentifier(imgViewId, "id", this.getPackageName());

        String iconOnIdString = String.format("%s_on", icontype_base);
        String iconOffIdString = String.format("%s_off", icontype_base);
        int iconOnId = this.getResources().getIdentifier(iconOnIdString, "drawable", this.getPackageName());
        int iconOffId = this.getResources().getIdentifier(iconOffIdString, "drawable", this.getPackageName());

        rv.setImageViewResource(imageViewId, level == 0 ? iconOffId : iconOnId);
    }

    private void getServerTime() {

        // Horrible hack until I find a proper way to get the current timestamp at server side : retrieve data from one arbitrary device and read its update timestamp
        String[] deviceList = getResources().getStringArray(R.array.deviceList);
        int tempArrayId = this.getResources().getIdentifier(deviceList[0], "array", this.getPackageName());
        String[] val = getResources().getStringArray(tempArrayId);
        String devId = val[0];
        String devInstanceId = val[1];
        String devCommandClass = val[2];

        String query_Get = String.format(this.getResources().getString(R.string.zwaveserver_base) + "/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].Get()", devId, devInstanceId, devCommandClass);
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base)+"/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].data.level", devId, devInstanceId, devCommandClass);

        httpRequest(query_Get);
        String result = httpRequest(query_Data);

        String updateTime ="";

        // Parse the received JSON data
        try {
            JSONObject jdata = new JSONObject(result);
            updateTime = jdata.getString("updateTime");
        } catch(JSONException e){
            Log.e("ZWaveWidgetService", "getServerTime: Error parsing data "+e.toString());
        }

        // Notify provider of this timestamp
        final Intent storeTimeIntent = new Intent(this, ZWaveWidgetProvider.class);
        storeTimeIntent.setAction(ZWaveWidgetProvider.STORE_REFRESH_TIME_ACTION);
        storeTimeIntent.putExtra(ZWaveWidgetProvider.STORE_REFRESH_TIME_EXTRA, Long.valueOf(updateTime));
        final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, storeTimeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            donePendingIntent.send();
        }
        catch (PendingIntent.CanceledException ce) {
            Log.i("ZWaveWidgetService", "getServerTime: Exception: " + ce.toString());
        }
    }

    private String httpRequest(String url) {
        String result = "";

      Log.i("ZWaveWidgetService", "Performing HTTP request " + url);

        try {

            URL targetUrl = new URL(url);
            HttpURLConnection urlConnection = (HttpURLConnection) targetUrl.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                result = readStream(in);
            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            Log.e("ZWaveWidgetService", "httpRequest: Error in http connection " + e.toString());
        }
/*
        String data;
        if (result.length() <= 2048)
            data = result;
        else
            data = "[long data....]";
*/
        Log.i("ZWaveWidgetService", "httpRequest completed, received " + result.length() + " bytes: " + result.replaceAll(" ", "").replaceAll("\r", "").replaceAll("\n", ""));

        return result;
    }

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while(i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

}