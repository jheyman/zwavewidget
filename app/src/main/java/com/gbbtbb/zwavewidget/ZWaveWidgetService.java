package com.gbbtbb.zwavewidget;

import android.app.IntentService;
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

        //final String action = intent.getAction();
        //Log.i("ZWaveWidgetService", "onHandleIntent action= " + action);

        RemoteViews rv = new RemoteViews(this.getPackageName(), R.layout.zwavewidget);

        String[] deviceList = getResources().getStringArray(R.array.deviceList);

        for (String d : deviceList) {
            int arrayId = this.getResources().getIdentifier(d, "array", this.getPackageName());
            String[] temp = getResources().getStringArray(arrayId);
            updateSwitch(rv, temp[0], temp[1], temp[2], temp[3], temp[4], temp[5], d);
        }

        ComponentName me = new ComponentName(this, ZWaveWidgetProvider.class);
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        mgr.updateAppWidget(me, rv);
    }

    private void updateSwitch(RemoteViews rv, String devId, String devInstanceId, String devCommandClass, String imgViewId, String txtViewId, String icontype_base, String name) {

/*
        Log.i("ZWaveWidgetService", "updateSwitch devid: " + devId);
        Log.i("ZWaveWidgetService", "updateSwitch devInstanceId: " + devInstanceId);
        Log.i("ZWaveWidgetService", "updateSwitch devCommandClass: " + devCommandClass);
        Log.i("ZWaveWidgetService", "updateSwitch imgViewId: " + imgViewId);
        Log.i("ZWaveWidgetService", "updateSwitch txtViewId: " + txtViewId);
        Log.i("ZWaveWidgetService", "updateSwitch name: " + name);
*/
        //String query_Get = String.format("http://192.168.0.13:8083/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].Get()", devId, devInstanceId, devCommandClass);
        //String query_Data = String.format("http://192.168.0.13:8083/ZAutomation/api/v1/devices/ZWayVDev_%s:%s:%s", devId, devInstanceId, devCommandClass);

        String query_Data = String.format(" http://192.168.0.13:8083/ZWaveAPI/Run/devices[%s].instances[%s].commandClasses[%s].data.level", devId, devInstanceId, devCommandClass);


        //httpRequest(query_Get);
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

