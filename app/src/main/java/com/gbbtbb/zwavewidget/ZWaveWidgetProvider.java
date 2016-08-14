package com.gbbtbb.zwavewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.RemoteViews;

public class ZWaveWidgetProvider extends AppWidgetProvider {

    public static final String CLICK_ACTION ="com.gbbtbb.zwavewidget.CLICK_ACTION";
    public static final String INITIALIZE_ACTION ="com.gbbtbb.zwavewidget.INITIALIZE_ACTION";
    public static final String REFRESH_ACTION ="com.gbbtbb.zwavewidget.REFRESH_ACTION";
    public static final String TOGGLE_ACTION ="com.gbbtbb.zwavewidget.TOGGLE_ACTION";

    public static final String STORE_REFRESH_TIME_ACTION ="com.gbbtbb.zwavewidget.STORE_REFRESH_TIME_ACTION";

    public static final String STORE_REFRESH_TIME_EXTRA ="com.gbbtbb.zwavewidget.STORE_REFRESH_TIME_EXTRA";
    public static final String LAST_REFRESH_EXTRA ="com.gbbtbb.zwavewidget.LAST_REFRESH_EXTRA";
    public static final String DEVICE_NAME_EXTRA ="com.gbbtbb.zwavewidget.DEVICE_NAME_EXTRA";

    public Handler handler = new Handler();
    private Context ctx;
    private static int REFRESH_DELAY = 15000;
    static long lastRefreshUnixTime = 0;

    Runnable refreshWidget = new Runnable()
    {
        @Override
        public void run() {

            // Call service to refresh widget data/UI
            Intent i = new Intent(ctx.getApplicationContext(), ZWaveWidgetService.class);
            i.putExtra(LAST_REFRESH_EXTRA, lastRefreshUnixTime);
            i.setAction(REFRESH_ACTION);
            ctx.startService(i);

            // REMOVED: use server received timestamps instead
            // Keep track of latest refresh time, and schedule next refresh
            //lastRefreshUnixTime = System.currentTimeMillis() / 1000L;


            handler.postDelayed(this, REFRESH_DELAY);
        }
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int count = appWidgetIds.length;

        ctx= context;
        handler.postDelayed(refreshWidget, REFRESH_DELAY);

        Log.i("ZWaveWidgetProvider", "onUpdate...");
        for (int i = 0; i < count; i++) {
            int widgetId = appWidgetIds[i];

            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.zwavewidget);

            // Parse the list of all declared devices, retrieve the associated imageView for each device, and register a click event on it
            String[] deviceList = context.getResources().getStringArray(R.array.deviceList);
            for (String d : deviceList) {
                int arrayId = context.getResources().getIdentifier(d, "array", context.getPackageName());
                String[] temp = context.getResources().getStringArray(arrayId);

                // Get resource identifier of ImageView for this device
                int imageViewId = context.getResources().getIdentifier(temp[6], "id", context.getPackageName());

                // Register a click intent on it
                final Intent onClickIntent = new Intent(context, ZWaveWidgetProvider.class);
                onClickIntent.setAction(ZWaveWidgetProvider.CLICK_ACTION);
                onClickIntent.putExtra(DEVICE_NAME_EXTRA, d);
                onClickIntent.setData(Uri.parse(onClickIntent.toUri(Intent.URI_INTENT_SCHEME)));
                final PendingIntent onClickPendingIntent = PendingIntent.getBroadcast(context, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(imageViewId, onClickPendingIntent);
            }

            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }

        // Get all ids
        ComponentName thisWidget = new ComponentName(context, ZWaveWidgetProvider.class);

        // Build the intent to call the service
        Intent intent = new Intent(context.getApplicationContext(), ZWaveWidgetService.class);
        //intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        // Update the widgets via the service
        intent.putExtra("lastRefresh", lastRefreshUnixTime);
        intent.setAction(INITIALIZE_ACTION);
        context.startService(intent);
        //lastRefreshUnixTime = System.currentTimeMillis() / 1000L;
        Log.i("ZWaveWidgetProvider", "onUpdate: background service started");
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();
        int[] appWidgetIds;
        //Log.i("ZWaveWidgetProvider", "onReceive " + action);

        if (CLICK_ACTION.equals(action)) {

            String deviceName = intent.getStringExtra(DEVICE_NAME_EXTRA);
            Log.i("ZWaveWidgetProvider", "onReceive CLICK devName=" + deviceName);

            // Build the intent to call the service
            Intent i = new Intent(ctx.getApplicationContext(), ZWaveWidgetService.class);
            i.putExtra(DEVICE_NAME_EXTRA, deviceName);
            i.setAction(TOGGLE_ACTION);
            ctx.startService(i);
        }
        else if (STORE_REFRESH_TIME_ACTION.equals(action)) {
            lastRefreshUnixTime = intent.getLongExtra(STORE_REFRESH_TIME_EXTRA, 0);
            Log.i("ZWaveWidgetProvider", "Updating latestRefreshUnixTime to " + Long.toString(lastRefreshUnixTime));
        }
        super.onReceive(ctx, intent);
    }
}

