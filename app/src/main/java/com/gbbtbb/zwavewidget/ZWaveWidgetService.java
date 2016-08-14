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

        /*
        0 <item name="devId_setget"></item>
        1 <item name="instanceId_setget"></item>
        2 <item name="commandclass_setget"></item>

        3 <item name="devId_notified"></item>
        4 <item name="instanceId_notified"></item>
        5 <item name="commandclass_notified"></item>

        6 <item name="imageViewId"></item>
        7 <item name="textViewId"></item>

        8 <item name="icontype"></item>
        */


        if (ZWaveWidgetProvider.INITIALIZE_ACTION.equals(action)) {
            Log.i("ZWaveWidgetService", "onHandleIntent INITIALIZE_ACTION");

            // do a get on an arbitrary device to get server current time
            String[] deviceList = getResources().getStringArray(R.array.deviceList);
            int tempArrayId = this.getResources().getIdentifier(deviceList[0], "array", this.getPackageName());
            String[] val = getResources().getStringArray(tempArrayId);
            initializeUpdateTime(rv, val[0], val[1], val[2], val[6], val[7], val[8], deviceList[0]);

            // now get data for every device
            for (String d : deviceList) {
                int arrayId = this.getResources().getIdentifier(d, "array", this.getPackageName());
                String[] temp = getResources().getStringArray(arrayId);
                forceRefreshDevice(rv, temp[0], temp[1], temp[2], temp[6], temp[7], temp[8], d);
            }
        } else if (ZWaveWidgetProvider.REFRESH_ACTION.equals(action)) {
            long lastRefreshTime = intent.getExtras().getLong(ZWaveWidgetProvider.LAST_REFRESH_EXTRA);
            Log.i("ZWaveWidgetService", "onHandleIntent REFRESH_ACTION date=" + Long.toString(lastRefreshTime));

            // Get all state changes since last update from the z-way server
            JSONObject jdata = getIncrementalUpdate(lastRefreshTime);

            // Parse the list of all declared devices, and figure out if a UI update is required
            String[] deviceList = getResources().getStringArray(R.array.deviceList);
            for (String d : deviceList) {
                int arrayId = this.getResources().getIdentifier(d, "array", this.getPackageName());
                String[] temp = getResources().getStringArray(arrayId);
                refreshDevice(jdata, rv, temp[3], temp[4], temp[5], temp[6], temp[7], temp[8], d);
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
    }

    private JSONObject getIncrementalUpdate(long timestamp){
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base) + "/ZWaveAPI/Data/%d", timestamp);
        String result = httpRequest(query_Data);
        //Log.i("ZWaveWidgetService", "getIncrementalUpdate: " + result);

        String updateTime ="";
        JSONObject jdata = null;
        // Parse the received JSON data
        try {
            jdata = new JSONObject(result);
            updateTime = jdata.getString("updateTime");

        } catch(JSONException e){
            Log.e("ZWaveWidgetService", "Error parsing data " + e.toString());
        }

        // Notify provider of data refresh time
        final Intent storeTimeIntent = new Intent(this, ZWaveWidgetProvider.class);
        storeTimeIntent.setAction(ZWaveWidgetProvider.STORE_REFRESH_TIME_ACTION);
        storeTimeIntent.putExtra(ZWaveWidgetProvider.STORE_REFRESH_TIME_EXTRA, Long.valueOf(updateTime));
        final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, storeTimeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            //Log.i("ZWaveWidgetService", "onHandleIntent: launching pending Intent for storing incremental state change refresh time");
            donePendingIntent.send();
        }
        catch (PendingIntent.CanceledException ce) {
            Log.i("ZWaveWidgetService", "onHandleIntent: Exception: " + ce.toString());
        }

        return jdata;
    }

    private void refreshDevice(JSONObject jdata, RemoteViews rv, String devId_notified, String devInstanceId_notified, String devCommandClass_notified, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Build the string to look for in the JSON data
        String key = String.format("devices.%s.instances.%s.commandClasses.%s.data.level", devId_notified, devInstanceId_notified, devCommandClass_notified);
        String level_string="";
        int level=0;

        // Parse the JSON data and check if this particular device changed state
        try {
            JSONObject j1 = jdata.getJSONObject(key);

            if (j1 != null) {
                level_string = j1.getString("value");

                if ("true".equals(level_string))
                    level = 1;
                else if ("false".equals(level_string))
                    level = 0;
                else
                    level = Integer.valueOf(j1.getString("value"));

                Log.i("ZWaveWidgetService", "found change for "+ key +" : level=" + Integer.toString(level));

                int imageViewId = this.getResources().getIdentifier(imgViewId, "id", this.getPackageName());

                String iconOnIdString = String.format("%s_on", icontype_base);
                String iconOffIdString = String.format("%s_off", icontype_base);
                int iconOnId = this.getResources().getIdentifier(iconOnIdString, "drawable", this.getPackageName());
                int iconOffId = this.getResources().getIdentifier(iconOffIdString, "drawable", this.getPackageName());

                rv.setImageViewResource(imageViewId, level == 0 ? iconOffId : iconOnId);

                int textViewId = this.getResources().getIdentifier(txtViewId, "id", this.getPackageName());
                rv.setTextViewText(textViewId, name);
            }
        } catch(JSONException e){
            //Log.i("ZWaveWidgetService", "No change detected for " + key );
        }
    }

    private void toggleDevice(RemoteViews rv, String devId, String devInstanceId, String devCommandClass, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Get current state of this device
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base)+"/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].data.level", devId, devInstanceId, devCommandClass);
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
            Log.e("ZWaveWidgetService", "Error parsing data "+e.toString());
        }

        // toggle state
        if (level == 0)
            level = 255; // 255 works even if max value is actually 1 or 99 or whatever.
        else
            level = 0;

        // Refresh icon
        int imageViewId = this.getResources().getIdentifier(imgViewId, "id", this.getPackageName());

        String iconOnIdString = String.format("%s_on", icontype_base);
        String iconOffIdString = String.format("%s_off", icontype_base);
        int iconOnId = this.getResources().getIdentifier(iconOnIdString, "drawable", this.getPackageName());
        int iconOffId = this.getResources().getIdentifier(iconOffIdString, "drawable", this.getPackageName());

        rv.setImageViewResource(imageViewId, level == 0 ? iconOffId : iconOnId);

        // Set new level on device
        String query_SetLevel = String.format(this.getResources().getString(R.string.zwaveserver_base)+"/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].Set(%d)", devId, devInstanceId, devCommandClass, level);
        httpRequest(query_SetLevel);
    }

    private void forceRefreshDevice(RemoteViews rv, String devId, String devInstanceId, String devCommandClass, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Perform an explicit GET command on the device, to be sure that device status is fresh
        String query_Get = String.format(this.getResources().getString(R.string.zwaveserver_base)+"/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].Get()", devId, devInstanceId, devCommandClass);
        String query_Data = String.format(this.getResources().getString(R.string.zwaveserver_base) + "/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].data.level", devId, devInstanceId, devCommandClass);

        httpRequest(query_Get);
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
            Log.e("ZWaveWidgetService", "Error parsing data "+e.toString());
        }

        int imageViewId = this.getResources().getIdentifier(imgViewId, "id", this.getPackageName());

        String iconOnIdString = String.format("%s_on", icontype_base);
        String iconOffIdString = String.format("%s_off", icontype_base);
        int iconOnId = this.getResources().getIdentifier(iconOnIdString, "drawable", this.getPackageName());
        int iconOffId = this.getResources().getIdentifier(iconOffIdString, "drawable", this.getPackageName());

        rv.setImageViewResource(imageViewId, level == 0 ? iconOffId : iconOnId);

        int textViewId = this.getResources().getIdentifier(txtViewId, "id", this.getPackageName());
        rv.setTextViewText(textViewId, name);
    }


    private void initializeUpdateTime(RemoteViews rv, String devId, String devInstanceId, String devCommandClass, String imgViewId, String txtViewId, String icontype_base, String name) {

        // Perform an explicit GET command on the device, to be sure that device status is fresh
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
            Log.e("ZWaveWidgetService", "Error parsing data "+e.toString());
        }

        // Notify provider of latest data refresh time
        final Intent storeTimeIntent = new Intent(this, ZWaveWidgetProvider.class);
        storeTimeIntent.setAction(ZWaveWidgetProvider.STORE_REFRESH_TIME_ACTION);
        storeTimeIntent.putExtra(ZWaveWidgetProvider.STORE_REFRESH_TIME_EXTRA, Long.valueOf(updateTime));
        final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, storeTimeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            //Log.i("ZWaveWidgetService", "onHandleIntent: launching pending Intent for storing incremental state change refresh time");
            donePendingIntent.send();
        }
        catch (PendingIntent.CanceledException ce) {
            Log.i("ZWaveWidgetService", "onHandleIntent: Exception: " + ce.toString());
        }
    }

    private String httpRequest(String url) {
        String result = "";

        //Log.i("ZWaveWidgetService", "Performing HTTP request " + url);

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
        if (result.length() <= 128)
            data = result;
        else
            data = "[long data....]";

        Log.i("ZWaveWidgetService", "httpRequest completed, received " + result.length() + " bytes: " + data);
*/
        Log.i("ZWaveWidgetService", "httpRequest completed, received " + result.length() + " bytes: " + result);
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