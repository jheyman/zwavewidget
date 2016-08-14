package com.gbbtbb.zwavewidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.RemoteViews;

public class ZWaveWidgetProvider extends AppWidgetProvider {

    private static String imageName = "";
    private static int imageHeight ;
    private static int imageWidth ;
    private static int imageOrientation ;

    public Handler handler = new Handler();
    private Context ctx;
    private static int REFRESH_DELAY = 15000;
    static long lastRefreshUnixTime = 0;

    Runnable refreshWidget = new Runnable()
    {
        @Override
        public void run() {
            Intent i = new Intent(ctx.getApplicationContext(), ZWaveWidgetService.class);
            //intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            // Update the widgets via the service
            i.putExtra("lastRefresh", lastRefreshUnixTime);
            i.setAction("refresh");
            ctx.startService(i);

            lastRefreshUnixTime = System.currentTimeMillis() / 1000L;
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
/*
            final Intent onClickIntent = new Intent(context, ZWaveWidgetProvider.class);
            onClickIntent.setAction(ZWaveWidgetProvider.SENDEMAIL_ACTION);
            //onClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            onClickIntent.setData(Uri.parse(onClickIntent.toUri(Intent.URI_INTENT_SCHEME)));
            final PendingIntent onClickPendingIntent = PendingIntent.getBroadcast(context, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.view00, onClickPendingIntent);
*/
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }

        // Get all ids
        ComponentName thisWidget = new ComponentName(context, ZWaveWidgetProvider.class);

        // Build the intent to call the service
        Intent intent = new Intent(context.getApplicationContext(), ZWaveWidgetService.class);
        //intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        // Update the widgets via the service
        intent.putExtra("lastRefresh", lastRefreshUnixTime);
        intent.setAction("initialUpdate");
        context.startService(intent);
        lastRefreshUnixTime = System.currentTimeMillis() / 1000L;
        Log.i("ZWaveWidgetProvider", "onUpdate: background service started");
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();
        int[] appWidgetIds;
        Log.i("ZWaveWidgetProvider", "onReceive " + action);

        super.onReceive(ctx, intent);
    }
}

