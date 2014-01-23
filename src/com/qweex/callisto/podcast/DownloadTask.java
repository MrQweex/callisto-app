/*
 * Copyright (C) 2012-2014 Qweex
 * This file is a part of Callisto.
 *
 * Callisto is free software; it is released under the
 * Open Software License v3.0 without warranty. The OSL is an OSI approved,
 * copyleft license, meaning you are free to redistribute
 * the source code under the terms of the OSL.
 *
 * You should have received a copy of the Open Software License
 * along with Callisto; If not, see <http://rosenlaw.com/OSL3.0-explained.htm>
 * or check OSI's website at <http://opensource.org/licenses/OSL-3.0>.
 */
package com.qweex.callisto.podcast;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Button;
import com.qweex.callisto.R;
import com.qweex.callisto.StaticBlob;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;

/** A class to start downloading a file outside the UI thread. */
public class DownloadTask extends AsyncTask<String, Object, Boolean>
{
    /** Info about episode */
    private String Title, Date, AudLink, VidLink, Show;
    /** Total size of the file to be downloaded */
    private long TotalSize, AudSize, VidSize;
    /** Target file to download to */
    private File Target, AudFile, VidFile;
    /** ID for download notification */
    private final int NOTIFICATION_ID = 3696;
    /** Timeout Parameters */
    private final int TIMEOUT_CONNECTION = 5000, TIMEOUT_SOCKET = 30000;
    /** For displaying the notification */
    private NotificationManager mNotificationManager;
    /** For notification */
    private PendingIntent contentIntent;
    /** Is the task running? */
    public static boolean running = false;
    /** Context, used for intent and pending intent for notification */
    public Context context;
    /** The number of times the CURRENT download has failed. */
    private int inner_failures;
    /** The number of times trying to re-download the entire list has failed??? */
    private int outer_failures = 0;
    /** The number of files that have failed and been passed.*/
    private int failed = 0;
    /** Limits for how often there can be failures */
    private final int INNER_LIMIT=5, OUTER_LIMIT=10;
    /** Used for the notification */
    NotificationCompat.Builder mBuilder;

    public static int downloading_count;

    public DownloadTask(Context c)
    {
        super();
        context = c;
        downloading_count = StaticBlob.databaseConnector.getActiveDownloads().getCount();
    }

    @Override
    protected void onPreExecute()
    {
        String TAG = StaticBlob.TAG();
        running = true;
        Log.i(TAG, "Beginning downloads");

        //Show notification
        Intent notificationIntent = new Intent(context, DownloadList.class);
        contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setContentTitle( this.context.getResources().getString(R.string.downloading) + " " + StaticBlob.current_download +
                " " +  this.context.getResources().getString(R.string.of) + " " + downloading_count)
                .setContentText(Show + ": " + Title)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentIntent(contentIntent)
                .setOngoing(true);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }


    @Override
    protected Boolean doInBackground(String... params)
    {
        String TAG = StaticBlob.TAG();
        boolean isVideo;
        Cursor current;

        long id = 0, identity = 0;
        Log.e(TAG, "Preparing to start: " + StaticBlob.databaseConnector.getActiveDownloads().getCount() + " downloads");
        boolean canceled = false;
        while(StaticBlob.databaseConnector.getActiveDownloads().getCount()>0)
        {
            if(isCancelled())   //Checks to see if it has been canceled by somewhere else
            {
                mNotificationManager.cancel(NOTIFICATION_ID);
                return false;
            }
            try
            {
                Cursor c = StaticBlob.databaseConnector.getActiveDownloads();
                c.moveToFirst();
                id = c.getLong(c.getColumnIndex("_id"));
                identity = c.getLong(c.getColumnIndex("identity"));
                isVideo = c.getInt(c.getColumnIndex("video"))>0;
                current = StaticBlob.databaseConnector.getOneEpisode(identity);
                current.moveToFirst();

                //Get info
                AudLink = current.getString(current.getColumnIndex("mp3link"));
                VidLink = current.getString(current.getColumnIndex("vidlink"));
                AudSize = current.getLong(current.getColumnIndex("mp3size"));
                VidSize = current.getLong(current.getColumnIndex("vidsize"));
                Title = current.getString(current.getColumnIndex("title"));
                Date = current.getString(current.getColumnIndex("date"));
                Show = current.getString(current.getColumnIndex("show"));
                Date = StaticBlob.sdfFile.format(StaticBlob.sdfRaw.parse(Date));

                //Getting target
                Target = new File(StaticBlob.storage_path + File.separator + Show);
                Target.mkdirs();
                if(Title.indexOf("|")>0)
                    Title = Title.substring(0, Title.indexOf("|"));
                Title=Title.trim();
                AudFile = new File(Target, Date + "__" + StaticBlob.makeFileFriendly(Title) + EpisodeDesc.getExtension(AudLink));
                if(VidLink!=null)
                    VidFile = new File(Target, Date + "__" + StaticBlob.makeFileFriendly(Title) + EpisodeDesc.getExtension(VidLink));
                Target = isVideo ? VidFile : AudFile;


                //Prepare the HTTP
                Log.i(TAG, "Path: " + Target.getPath());
                URL url = new URL(isVideo ? VidLink : AudLink);
                Log.i(TAG, "Starting download: " + url.toString());

                Log.i(TAG, "Opening the connection...");
                HttpURLConnection ucon = (HttpURLConnection) url.openConnection();
                String lastModified = ucon.getHeaderField("Last-Modified");
                ucon = (HttpURLConnection) url.openConnection();
                if(Target.exists())
                {
                    ucon.setRequestProperty("Range", "bytes=" + Target.length() + "-");
                    ucon.setRequestProperty("If-Range", lastModified);
                }
                ucon.setReadTimeout(TIMEOUT_CONNECTION);
                ucon.setConnectTimeout(TIMEOUT_SOCKET);
                ucon.connect();

                //Notification
                mBuilder.setProgress(100, 0, true)
                    .setContentTitle(this.context.getResources().getString(R.string.downloading) + " " +
                        StaticBlob.current_download + " " +
                        this.context.getResources().getString(R.string.of) + " " + downloading_count)
                    .setContentText(Show + ": " + Title);
                // Displays the progress bar for the first time.
                mNotificationManager.notify(NOTIFICATION_ID,mBuilder.build() );

                //Actually do the DLing
                InputStream is = ucon.getInputStream();
                TotalSize = ucon.getContentLength() + Target.length();
                BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);
                FileOutputStream outStream;
                byte buff[];
                Log.i(TAG, "mmk skipping the downloaded portion..." + Target.length() + " of " + TotalSize);
                if(Target.exists()) //Append if it exists
                    outStream = new FileOutputStream(Target, true);
                else
                    outStream = new FileOutputStream(Target);
                buff = new byte[5 * 1024];
                Log.i(TAG, "Getting content length (size)");
                int len = 0;
                long downloadedSize = Target.length(),
                        percentDone = 0;

                //SPEED_COUNT == the number of times through the buffer loop to go through before updating the speed
                // currentDownloadLoopIndex == ???
                // lastTime == The time that the last speed was calculated at
                // all_spds == All speeds tabulated thus far from currentDownloadLoopIndex to SPEED_COUNT
                // avg_speed == the average speed when SPEED_COUNT is reached
                int SPEED_COUNT = 200,
                        currentDownloadLoopIndex = 0;
                long lastTime = (new java.util.Date()).getTime(),
                        all_spds = 0;
                double avg_speed = 0;
                DecimalFormat df = new DecimalFormat("#.##");

                //Wifi lock
                WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if(DownloadList.Download_wifiLock==null)
                    DownloadList.Download_wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL , "Callisto_download");
                if(!DownloadList.Download_wifiLock.isHeld())
                    DownloadList.Download_wifiLock.acquire();

                Log.i(TAG, "FINALLY starting the download");
                inner_failures = 0;
                //-----------------Here is where the actual downloading happens----------------
                while (len != -1)
                {
                    Cursor active = StaticBlob.databaseConnector.getActiveDownloads();
                    if(StaticBlob.databaseConnector.getActiveDownloads().getCount()==0)
                        canceled = true;
                    else
                    {
                        active.moveToFirst();
                        canceled = active.getLong(active.getColumnIndex("identity"))!=identity;
                    }
                    if(canceled)
                    {
                        Log.i(TAG, "Download has been canceled, deleting.");
                        Target.delete();
                        break;
                    }
                    if(isCancelled())
                    {
                        mNotificationManager.cancel(NOTIFICATION_ID);
                        return false;
                    }

                    try
                    {
                        len = inStream.read(buff);
                        if(len==-1)
                            break;

                        outStream.write(buff,0,len);
                        downloadedSize += len;
                        percentDone = downloadedSize*100;
                        percentDone /= TotalSize;

                        //Add to the average speed
                        long temp_spd = 0;
                        long time_diff = ((new java.util.Date()).getTime() - lastTime);
                        if(time_diff>0)
                        {
                            temp_spd= len*100/time_diff;
                            currentDownloadLoopIndex++;
                            all_spds += temp_spd;
                            lastTime = (new java.util.Date()).getTime();
                        }

                    } catch (IOException e) {
                        Log.e(TAG+":IOException", "IO is a moon - " + inner_failures);
                        inner_failures++;
                        if(inner_failures==INNER_LIMIT)
                            break;
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e1) {}
                        //Add failure to average
                        currentDownloadLoopIndex++;
                        lastTime = (new java.util.Date()).getTime();

                    } catch (Exception e) {
                        Log.e(TAG+":??Exception", e.getClass() + " : " + e.getMessage());
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e1) {}
                        //Add failure to average
                        currentDownloadLoopIndex++;
                        lastTime = (new java.util.Date()).getTime();
                    }

                    //If the time is right, get the average
                    if(currentDownloadLoopIndex>=SPEED_COUNT)
                    {
                        avg_speed = all_spds*1.0/currentDownloadLoopIndex/100;
                        all_spds = 0;
                        currentDownloadLoopIndex = 0;

                        if(DownloadList.thisInstance!=null)
                        {
                            DownloadList.thisInstance.runOnUiThread(DownloadList.thisInstance.new DownloadProgressUpdater(
                                    (int)(TotalSize/1000), (int)(downloadedSize/1000)
                            ));
                        }


                        mBuilder.setProgress((int)(TotalSize/1000), (int)(downloadedSize/1000), false)
                            .setContentTitle(this.context.getResources().getString(R.string.downloading) + " " +
                                StaticBlob.current_download + " " +
                                this.context.getResources().getString(R.string.of) + " " + downloading_count +
                                " - " + percentDone + "%  (" +
                                df.format(avg_speed) + "kb/s)")
                            .setContentText(Show + ": " + Title);
                        // Displays the progress bar for the first time.
                        mNotificationManager.notify(NOTIFICATION_ID,mBuilder.build() );
                    }
                }   // END download of single file

                outStream.flush();
                outStream.close();
                inStream.close();
                if(inner_failures==INNER_LIMIT)
                {
                    throw new Exception("Inner exception has passed " + INNER_LIMIT);
                }

                Cursor active = StaticBlob.databaseConnector.getActiveDownloads();
                if(StaticBlob.databaseConnector.getActiveDownloads().getCount()==0)
                    canceled = true;
                else
                {
                    active.moveToFirst();
                    canceled = active.getLong(active.getColumnIndex("identity"))!=identity;
                }
                if(!canceled)
                {
                    Log.i(TAG, "Trying to finish with " + Target.length() + "==" + TotalSize);
                    if(Target.length()==TotalSize)
                    {
                        StaticBlob.current_download++;

                        Log.i(TAG, (inner_failures<INNER_LIMIT?"Successfully":"FAILED") + " downloaded to : " + Target.getPath());

                        //Move the download from active to completed.
                        StaticBlob.databaseConnector.markDownloadComplete(id);

                        Log.i(TAG, " " + DownloadList.rebuildHeaderThings);
                        if(DownloadList.rebuildHeaderThings !=null)
                            DownloadList.rebuildHeaderThings.sendEmptyMessage(0);

                        boolean queue = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("download_to_queue", false);
                        if(queue)
                        {
                            StaticBlob.databaseConnector.appendToQueue(identity, false, isVideo);
                            StaticBlob.playerInfo.update(context);
                        }
                        //Episode Desc
                        if(EpisodeDesc.currentInstance!=null)
                            EpisodeDesc.currentInstance.determineButtons();
                        //ShowList
                        if(ShowList.thisInstance!=null && ShowList.thisInstance.currentDownloadItem!=null)
                        {
                            ShowList.thisInstance.runOnUiThread(ShowList.thisInstance.new updateBoldOrItalic(id, ShowList.thisInstance.currentDownloadItem, AudFile, VidFile, AudSize, VidSize));
                            ShowList.thisInstance.currentDownloadItem = null;
                        }
                    }
                } else
                    Target.delete();
            } catch (ParseException e) {
                Log.e(TAG+":ParseException", "Error parsing a date from the SQLite db: ");
                Log.e(TAG+":ParseException", Date);
                Log.e(TAG+":ParseException", "(This should never happen).");
                outer_failures++;
                e.printStackTrace();
            } catch(Exception e) {
                outer_failures++;
                Log.e(TAG+":Exception " + e.getClass(), "[" + outer_failures + "] Msg: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
            }
            if(outer_failures==OUTER_LIMIT)
            {
                boolean quit = false;
                //if(quit = aDownloads.charAt(1)=='x')
                //    aDownloads = aDownloads.replaceAll("x","");
                quit = true;
                if(DownloadList.rebuildHeaderThings !=null)
                    DownloadList.rebuildHeaderThings.sendEmptyMessage(0);
                failed++;
                outer_failures=0;

                if(quit)
                    break;
            }
        }
        Log.i(TAG, "Finished Downloading");
        if(DownloadList.thisInstance!=null)
            DownloadList.thisInstance.updateMenu();

        //Wifi lock
        if(DownloadList.Download_wifiLock!=null && DownloadList.Download_wifiLock.isHeld())
            DownloadList.Download_wifiLock.release();

        //Notification
        mNotificationManager.cancel(NOTIFICATION_ID);
        if(downloading_count>0)
        {


            mBuilder.setProgress(100, 0, false)
                    .setContentTitle("Finished downloading " + downloading_count + " files");
            if(failed>0)
                mBuilder.setContentText(failed + " failed, try them again later");
            mBuilder.setAutoCancel(true);
            mBuilder.setOngoing(false);

            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
            StaticBlob.current_download=1;
            downloading_count=0;
        }
        else
        {
            StaticBlob.current_download=1;
            downloading_count=0;
            return false;
        }
        return true;
    }



    @Override
    protected void onPostExecute(Boolean result)
    {
        //TODO: Is this even right
        running = false;
        Button streamButton = ((Button)((android.app.Activity)context).findViewById(R.id.stream)),
                downloadButton = ((Button)((android.app.Activity)context).findViewById(R.id.download));
        if(streamButton==null || downloadButton==null)
            return;
        if(result)
        {
            streamButton.setText(this.context.getResources().getString(R.string.play));
            streamButton.setOnClickListener(((EpisodeDesc)context).launchPlay);
            downloadButton.setText(this.context.getResources().getString(R.string.delete));
            downloadButton.setOnClickListener(((EpisodeDesc)context).launchDelete);
        } else
        {
            streamButton.setText(this.context.getResources().getString(R.string.stream));
            streamButton.setOnClickListener(((EpisodeDesc)context).launchStream);
            downloadButton.setText(this.context.getResources().getString(R.string.download));
            downloadButton.setOnClickListener(((EpisodeDesc)context).launchDownload);
        }
    }
}