/*
Copyright (C) 2012 Qweex
This file is a part of Callisto.

Callisto is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

Callisto is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Callisto; If not, see <http://www.gnu.org/licenses/>.
*/
package com.qweex.callisto;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Activity;
import android.app.Notification;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;



//Task Tags: todo clean feature fixme wtf idea

//CLEAN: Rename IDs in layout XML
//FEATURE: Widget
//IDEA: In-app Donate
//IDEA: Separate section for radio
//IDEA: player real estate with landscape
//IDEA: Maybe switch to DownloadManager.class? Requires API 9



/* This is the main activity/class for the app.
 * It contains the main screen, and also some static elements
 * that are globally used across multiple activities
 */

public class Callisto extends Activity {

	//-----Static members-----
	// These are used across the multiple activities
	public static MediaPlayer mplayer = null, live_player;
	public static DatabaseConnector databaseConnector;
	public static Notification notification_download;
	public static Notification notification_playing;
	public static Notification notification_chat;
	public static String storage_path;
	public static int downloading_count = 0;
	public static int current_download = 1;
	public static ArrayList<Long> download_queue = new ArrayList<Long>();
	public static final SimpleDateFormat sdfRaw = new SimpleDateFormat("yyyyMMddHHmmss");
	public static final SimpleDateFormat sdfSource = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
	public static final SimpleDateFormat sdfDestination = new SimpleDateFormat("MM/dd/yyyy");
	public static final SimpleDateFormat sdfFile = new SimpleDateFormat("yyyy-MM-dd");
	public static final SimpleDateFormat sdfHuman = new SimpleDateFormat("MMM d");
	public static final SimpleDateFormat sdfHumanLong = new SimpleDateFormat("MMM d, yyyy");
	public static Drawable playDrawable, pauseDrawable;
	public static PlayerInfo playerInfo = null;
	public static Resources RESOURCES;
	
	//------Local variables-----
	static TextView timeView;
	static int current;
	static ProgressBar timeProgress;
	private static final int QUIT_ID=Menu.FIRST+1;
	private static final int SETTINGS_ID=QUIT_ID+1;
	private static final int SAVE_POSITION_EVERY = 4;	//Cycles, not necessarily seconds
	private Timer timeTimer = null;
	
	public final String DONATION_APP_ID = "com.qweex.donation";
	public static oOnCompletionListener nextTrack;
	public static oOnErrorListener nextTrackBug;
	public static oOnPreparedListener okNowPlay;
	private static final int NOTIFICATION_ID = 1337;
	private static NotificationManager mNotificationManager;


	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		RESOURCES = getResources();
		mNotificationManager =  (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Callisto.storage_path = PreferenceManager.getDefaultSharedPreferences(this).getString("storage_path", "callisto");
		
		//This is the most reliable way I've found to determine if it is landscape
		boolean isLandscape = getWindowManager().getDefaultDisplay().getWidth() > getWindowManager().getDefaultDisplay().getHeight();
		
		
		setContentView(R.layout.main);
		findViewById(R.id.playPause).setOnClickListener(Callisto.playPause);
		findViewById(R.id.playlist).setOnClickListener(Callisto.playlist);
		findViewById(R.id.seek).setOnClickListener(Callisto.seekDialog);
		findViewById(R.id.next).setOnClickListener(Callisto.next);
		findViewById(R.id.previous).setOnClickListener(Callisto.previous);
		
		
		//This loop sets the onClickListeners and adjusts the button settings if the view is landscape
		int [] buttons = {R.id.listen, R.id.live, R.id.plan, R.id.chat, R.id.contact, R.id.donate}; //IDEA: add watch
		int [] graphics = {R.drawable.ic_menu_play_clip, R.drawable.ic_menu_view, R.drawable.ic_menu_today, R.drawable.ic_menu_allfriends, R.drawable.ic_menu_send, R.drawable.ic_menu_emoticons};
		
		Button temp;
		float dp = getResources().getDisplayMetrics().density;
		for(int i=0; i<buttons.length; i++)
		{
			temp = (Button)findViewById(buttons[i]);
			temp.setOnClickListener(startAct);
			
			
			if(isLandscape)
			{
				ViewGroup.LayoutParams tr = temp.getLayoutParams();
				((MarginLayoutParams) tr).setMargins(0, 0, 0, 0);
				temp.setPadding((int) (10*dp), 0, 0, 0);
				temp.setLayoutParams(tr);
				temp.setCompoundDrawablesWithIntrinsicBounds(graphics[i], 0, 0, 0);
			}
			//*/
		}
		
		//Initialization of (some of the) staticvariables
		Callisto.playDrawable = getResources().getDrawable(R.drawable.ic_media_play);
		Callisto.pauseDrawable = getResources().getDrawable(R.drawable.ic_media_pause);
		Callisto.databaseConnector = new DatabaseConnector(Callisto.this);
		Callisto.databaseConnector.open();
		Callisto.playerInfo = new PlayerInfo(Callisto.this);
		
		
		nextTrack = new oOnCompletionListener();
		nextTrackBug = new oOnErrorListener();
	    okNowPlay = new oOnPreparedListener();
	}
	
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		Log.v("Callisto:onResume", "Resuming main activity");
		Callisto.playerInfo.update(Callisto.this);
	}
	
	/* This method creates the layout for various activities, adding the Player Controls.
	 *  It essentially takes whatever "mainView" is and wraps it and the Controls in a vertical LinearLayout
	 */
    public static void build_layout(Context c, View mainView)
    {
    	Log.v("*:build_layout", "Building the layout");
		View controls = ((LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.controls, null, false);
		TextView empty = new TextView(c);
		empty.setId(android.R.id.empty);
		empty.setBackgroundColor(c.getResources().getColor(R.color.backClr));
		empty.setTextColor(c.getResources().getColor(R.color.txtClr));
		
		LinearLayout layout = new LinearLayout(c);
		layout.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams mParams = new LinearLayout.LayoutParams
				(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f);
		LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams
				(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0f);
		layout.addView(mainView, mParams);
		layout.addView(empty, mParams);
		layout.addView(controls, cParams);
		((Activity)c).setContentView(layout);
		((Activity)c).findViewById(R.id.playPause).setOnClickListener(Callisto.playPause);
		((Activity)c).findViewById(R.id.playlist).setOnClickListener(Callisto.playlist);
		((Activity)c).findViewById(R.id.seek).setOnClickListener(Callisto.seekDialog);
		((Activity)c).findViewById(R.id.next).setOnClickListener(Callisto.next);
		((Activity)c).findViewById(R.id.previous).setOnClickListener(Callisto.previous);
    }
    
    public static OnClickListener next = new OnClickListener()
    {
    	@Override public void onClick(View v)
    	{
    		playTrack(v.getContext(), 1, !Callisto.playerInfo.isPaused);
		}
	};
    public static OnClickListener previous = new OnClickListener()
    {
    	@Override public void onClick(View v)
    	{
    		playTrack(v.getContext(), -1, !Callisto.playerInfo.isPaused);
		}
	};
    
	/* This method plays the next song in the queue, if there is one. 
	 * previousOrNext = + if it should play the next track, - for the previous, and 0 for the current
	 */
    public static void playTrack(Context c, int previousOrNext, boolean startPlaying)
    {    	
		Cursor queue = Callisto.databaseConnector.advanceQueue(previousOrNext);
    	//If there are no items in the queue, stop the player
    	if(queue==null || queue.getCount()==0)
    	{
    		Log.v("*:playTrack", "Queue is empty. Pausing.");
	    	ImageButton x = (ImageButton) ((Activity)c).findViewById(R.id.playPause);
	    	if(x!=null)
	    		x.setImageDrawable(Callisto.pauseDrawable);
	    	if(Callisto.mplayer!=null)
	    		Callisto.mplayer.stop();
	    	mNotificationManager.cancel(NOTIFICATION_ID);
    		return;
    	}
    	
    	Log.v("*:playTrack", "Queue Size: " + queue.getCount());
    	queue.moveToFirst();
    		//The queue merely stores the identity (_id) of the entrie's position in the main SQL
    		//After obtaining it, we can get all the information about it
    	Long id = queue.getLong(queue.getColumnIndex("_id"));
    	Long identity = queue.getLong(queue.getColumnIndex("identity"));
    	boolean isStreaming = queue.getInt(queue.getColumnIndex("streaming"))>0;
        Cursor db = Callisto.databaseConnector.getOneEpisode(identity);
	    db.moveToFirst();
	    
	    String media_location;
	    Callisto.playerInfo.title = db.getString(db.getColumnIndex("title"));
	    Callisto.playerInfo.position = db.getInt(db.getColumnIndex("position"));
	    System.out.println("Position=" + Callisto.playerInfo.position);
	    Callisto.playerInfo.date = db.getString(db.getColumnIndex("date"));
	    Callisto.playerInfo.show = db.getString(db.getColumnIndex("show"));
	    Log.i("*:playTrack", "Loading info: " + Callisto.playerInfo.title);
	    if(isStreaming)
	    {
	    	media_location = db.getString(db.getColumnIndex("mp3link"));
	    }
	    else
	    {
		    try {
	        	Callisto.playerInfo.date = Callisto.sdfFile.format(Callisto.sdfRaw.parse(playerInfo.date));
			} catch (ParseException e) {
				Log.e("*playTrack:ParseException", "Error parsing a date from the SQLite db:");
				Log.e("*playTrack:ParseException", playerInfo.date);
				Log.e("*playTrack:ParseException", "(This should never happen).");
				e.printStackTrace();
				Toast.makeText(c, RESOURCES.getString(R.string.queue_error), Toast.LENGTH_SHORT).show();
				Callisto.databaseConnector.deleteQueueItem(id);
				return;
			}
		    
	        File target = new File(Environment.getExternalStorageDirectory(), Callisto.storage_path + File.separator + Callisto.playerInfo.show);
	        target = new File(target,playerInfo.date + "__" + Callisto.playerInfo.title + ".mp3");
	        if(!target.exists())
	        {
	        	Log.e("*:playTrack", "File not found: " + target.getPath());
	        	Toast.makeText(c, RESOURCES.getString(R.string.queue_error), Toast.LENGTH_SHORT).show();;
	        	Callisto.databaseConnector.deleteQueueItem(id);
				return;
	        }
	        media_location = target.getPath();
	    }
	    
		Intent notificationIntent = new Intent(c, EpisodeDesc.class);
		notificationIntent.putExtra("id", identity);
		PendingIntent contentIntent = PendingIntent.getActivity(c, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    	Callisto.notification_playing = new Notification(R.drawable.callisto, Callisto.RESOURCES.getString(R.string.playing), System.currentTimeMillis());
		Callisto.notification_playing.flags = Notification.FLAG_ONGOING_EVENT;
       	Callisto.notification_playing.setLatestEventInfo(c,  Callisto.playerInfo.title,  Callisto.playerInfo.show, contentIntent);
       	
       	mNotificationManager.notify(Callisto.NOTIFICATION_ID, Callisto.notification_playing);
	    
       	
		try {
			if(Callisto.mplayer==null)
				Callisto.mplayer = new MediaPlayer(); //This could be a problem
			Callisto.mplayer.reset();
			Callisto.okNowPlay.setContext(c);
			Callisto.mplayer.setDataSource(media_location);
			Callisto.mplayer.setOnCompletionListener(Callisto.nextTrack);
			Callisto.mplayer.setOnErrorListener(Callisto.nextTrackBug);
			if(!startPlaying)
				return;
			Callisto.mplayer.setOnPreparedListener(okNowPlay);
			Log.i("*:playTrack", "Preparing...");
			if(isStreaming)
				okNowPlay.pd = ProgressDialog.show(c, Callisto.RESOURCES.getString(R.string.loading), Callisto.RESOURCES.getString(R.string.loading_msg), true, false);
			Callisto.mplayer.prepareAsync();
			//FIXME: EXCEPTIONS
		} catch (IllegalArgumentException e) {
			Log.e("*playTrack:IllegalArgumentException", "Error attempting to set the data path for MediaPlayer:");
			Log.e("*playTrack:IllegalArgumentException", media_location);
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
			Log.e("*playTrack:IllegalStateException", "Error in State for MediaPlayer:");
		} catch (IOException e) {
			Log.e("*playTrack:IOException", "IO is another of Jupiter's moons. Did you know that?");
			e.printStackTrace();
		}
    }
    
    
	/* This class is essentially a struct with a few functions
	 * to handle the player, particularly updating it when switching activities.
	 */ 
    public class PlayerInfo
    {
    	public MediaPlayer player;
    	public String title, show, date;
    	public int position = 0, length = 0;
    	public boolean isPaused = true;
    	
    	public PlayerInfo(Context c)
    	{
    		TextView titleView = (TextView) ((Activity)c).findViewById(R.id.titleBar);
    		Log.v("PlayerInfo()", "Initializing PlayerInfo, queue size=" +  Callisto.databaseConnector.queueCount());
			titleView.setText(RESOURCES.getString(R.string.queue_size) + ": " + Callisto.databaseConnector.queueCount());
    	}
    	
    	public void update(Context c)
    	{
    		Callisto.nextTrack.setContext(c);
    		if(Callisto.mplayer!=null)
    		{
    			length = Callisto.mplayer.getDuration()/1000;
    			position = Callisto.mplayer.getCurrentPosition()/1000;
    		}
    		
    		Log.v("*:update", "Update - Title: " + title);
    		
    		//titleView
    	    TextView titleView = (TextView) ((Activity)c).findViewById(R.id.titleBar);
    	    if(titleView==null)
    	    	Log.w("Callisto:update", "Could not find view: " + "titleView");
    	    else
        	    if(title==null)
        	    	titleView.setText("Playlist size: " + Callisto.databaseConnector.queueCount());
        	    else
        	    	titleView.setText(title + " - " + show);
    	    
    	    //timeView
	    	Callisto.timeView = (TextView) ((Activity)c).findViewById(R.id.timeAt);
	    	if(Callisto.timeView==null)
    	    	Log.w("Callisto:update", "Could not find view: " + "TimeView");
    	    else
	    		timeView.setText(formatTimeFromSeconds(position));
    	    
	    	//lengthView
    	    TextView lengthView = (TextView) ((Activity)c).findViewById(R.id.length);
    	    if(lengthView==null)
    	    	Log.w("Callisto:update", "Could not find view: " + "lengthView");
    	    else
    	    	lengthView.setText(formatTimeFromSeconds(length));
    	    
    	    //timeProgress
    	    Callisto.timeProgress = (ProgressBar) ((Activity)c).findViewById(R.id.timeProgress);
    	    if(Callisto.timeProgress==null)
    	    	Log.w("Callisto:update", "Could not find view: " + "timeProgress");
    	    else
    	    {
	    	    timeProgress.setMax(length);
	    	    timeProgress.setProgress(position);
    	    }
        	
        	
        	ImageButton play = (ImageButton) ((Activity)c).findViewById(R.id.playPause);
        	if(play==null)
    	    	Log.w("Callisto:update", "Could not find view: " + "playPause");
    	    else
    	    {
				if(Callisto.playerInfo.isPaused)
					play.setImageDrawable(Callisto.playDrawable);
				else
					play.setImageDrawable(Callisto.pauseDrawable);
    	    }
        	
        	if(timeTimer==null)
        	{
        		Log.i("Callisto:PlayerInfo:update","Starting timer");
        		timeTimer = new Timer();
        		timeTimer.schedule(new TimerTask() {			
    			@Override
    			public void run() {
    				TimerMethod();
    			}
        		}, 0, 250);
        	}
    	}
    	
    }
    
	private void TimerMethod()
	{
		Callisto.this.runOnUiThread(TimerRunnable);
	}
	
	Runnable TimerRunnable = new Runnable()
	{
		int i=0;
		public void run()
		{
			if(Callisto.mplayer==null || !Callisto.mplayer.isPlaying())
				return;
			i++;
			Callisto.playerInfo.position = Callisto.mplayer.getCurrentPosition();
			current = Callisto.playerInfo.position/1000;
			timeProgress.setProgress(current);
			timeView.setText(formatTimeFromSeconds(current));
			if(i==Callisto.SAVE_POSITION_EVERY)
			{
				Log.v("Callisto:TimerMethod", "Updating position: " + Callisto.playerInfo.position);
		    	Cursor queue = Callisto.databaseConnector.currentQueue();
		    	queue.moveToFirst();
		    	Long identity = queue.getLong(queue.getColumnIndex("identity"));
				Callisto.databaseConnector.updatePosition(identity, Callisto.mplayer.getCurrentPosition());
				i=0;
			}
		}
	};
	
    
    //Listener for play/pause button
	public static OnClickListener playPause = new OnClickListener() 
    {
		@Override
		  public void onClick(View v) 
		  {
			if(Callisto.mplayer==null)
			{
				Log.d("*:playPause","PlayPause initiated");
				Callisto.mplayer = new MediaPlayer();
				Callisto.mplayer.setOnCompletionListener(Callisto.nextTrack);
				Callisto.mplayer.setOnErrorListener(Callisto.nextTrackBug);
				Callisto.playTrack(v.getContext(), 0, true);
			}
			else
			{
				if(Callisto.playerInfo.isPaused)
				{
					Callisto.mplayer.start();
					Callisto.playerInfo.isPaused = false;
					((ImageButton)v).setImageDrawable(Callisto.pauseDrawable);
				}
				else
				{
					Callisto.mplayer.pause();
					Callisto.playerInfo.isPaused = true;
					((ImageButton)v).setImageDrawable(Callisto.playDrawable);
				}
			}
		  }
    };
    
	//Converts a time from seconds to mm:ss or HH:mm:ss 
    public static String formatTimeFromSeconds(int seconds)
    {
  	  int minutes = seconds / 60;
  	  seconds %= 60;
  	  if(minutes>=60)
  	  {
  		  int hours = minutes / 60;
  		  minutes %= 60;
  		  return ((Integer.toString(hours) + ":" + 
  				 (minutes<10?"0":"") + Integer.toString(minutes) + ":" + 
  				 (seconds<10?"0":"") + Integer.toString(seconds)));
  	  }
  	  else
  		  return ((Integer.toString(minutes) + ":" + 
  				 (seconds<10?"0":"") + Integer.toString(seconds)));
    }
    
    //Listener for playlist button
    public static OnClickListener playlist = new OnClickListener() 
    {
		@Override
		  public void onClick(View v) 
		  {
			Intent newIntent = new Intent(v.getContext(), Queue.class);
			v.getContext().startActivity(newIntent);
		  }
    };
    
    //Listener to start the different activities
    OnClickListener startAct = new OnClickListener()
    {
		@Override
		  public void onClick(View v) 
		  {
			Intent newIntent;
			if(v.getId()==R.id.donate)
			{
				Toast.makeText(getApplicationContext(), "This will open a link to a donation 'app' in Google Play.", Toast.LENGTH_LONG).show();
				if(v.getId()==R.id.donate)
					return;
				//TODO: Change this when you launch
				newIntent = new Intent(Intent.ACTION_VIEW);
				try {
					newIntent.setData(Uri.parse("market://details?id=" + DONATION_APP_ID));
					startActivity(newIntent);
				} catch(ActivityNotFoundException e)
				{
					newIntent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + DONATION_APP_ID));
					startActivity(newIntent);
				}
			}
			else {
				   newIntent = new Intent(Callisto.this,
					v.getId()==R.id.live ? NowPlaying.class : (
					v.getId()==R.id.plan ? CalendarActivity.class : (
					v.getId()==R.id.chat ? IRCChat.class : (
					v.getId()==R.id.contact ? ContactForm.class : (
							  AllShows.class)))));
				   //newIntent.putExtra("is_video", (v.getId()==R.id.watch?true:false));	//IDEA: add watch
				   startActivity(newIntent);
			}
		  }
    };
    
    public static OnClickListener seekDialog = new OnClickListener()
    {
		@Override
		  public void onClick(View v) 
		  {
	    	SeekBar sb = new SeekBar(v.getContext());
	    	AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext()).setView(sb);
	    	final AlertDialog alertDialog = builder.create();
	    	
	    	alertDialog.setTitle(RESOURCES.getString(R.string.seek_title));
	    	alertDialog.setMessage(formatTimeFromSeconds(Callisto.mplayer.getCurrentPosition()/1000) + "/" + formatTimeFromSeconds(Callisto.playerInfo.length));
	    	sb.setMax(Callisto.playerInfo.length);
	    	sb.setProgress(Callisto.mplayer.getCurrentPosition()/1000);
	    	
	    	alertDialog.setButton(Callisto.RESOURCES.getString(android.R.string.yes), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					arg0.dismiss();
				}
	    	});//*/
	        alertDialog.show();
	        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
	            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
	                //Do something here with new value
	            	alertDialog.setMessage(formatTimeFromSeconds(progress) + "/" + formatTimeFromSeconds(Callisto.playerInfo.length));
	            }
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {}
				@Override
				
				public void onStopTrackingTouch(SeekBar arg0) {
					Callisto.mplayer.seekTo(arg0.getProgress()*1000);
				}
	        });
		  }
    };
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
	//Updates a show
	public static Message updateShow(int currentShow, SharedPreferences showSettings, boolean isVideo)
	{
		  Log.i("*:updateShow", "Beginning update");
		  String epDate = null, epTitle = null, epDesc = null;
		  String lastChecked = showSettings.getString("last_checked", null);
		  
		  String newLastChecked = null;
	   	  try
  	  {
	   		  //Prepare the parser
  		  XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
  		  factory.setNamespaceAware(true);
  		  XmlPullParser xpp = factory.newPullParser();
  		  URL url = new URL(isVideo ? AllShows.SHOW_LIST_VIDEO[currentShow] : AllShows.SHOW_LIST_AUDIO[currentShow]);
  		  InputStream input = url.openConnection().getInputStream();
  		  xpp.setInput(input, null);
  		  
  		  Log.v("*:updateShow", "Parser is prepared");
  		  //Skip the first heading (info about the show)
  		  int eventType = xpp.getEventType();
  		  while(!("item".equals(xpp.getName()) && eventType == XmlPullParser.START_TAG))
  		  {
				  eventType = xpp.next();
				  if(eventType==XmlPullParser.END_DOCUMENT)
					  throw(new UnfinishedParseException("Item"));
			  }
  		  eventType = xpp.next();
  		  Log.v("*:updateShow", "Parser has skipped heading");
  		  
  		  //Get episodes
  		  while(eventType!=XmlPullParser.END_DOCUMENT)
  		  {
				  //Title
				  while(!("title".equals(xpp.getName()) && eventType == XmlPullParser.START_TAG))
				  {
					  eventType = xpp.next();
					  if(eventType==XmlPullParser.END_DOCUMENT)
						  throw(new UnfinishedParseException("Title"));
				  }
				  eventType = xpp.next();
				  epTitle = xpp.getText();
				  if(epTitle==null)
					  throw(new UnfinishedParseException("Title"));
				  if(epTitle.indexOf("|")>0)
						epTitle = epTitle.substring(0, epTitle.indexOf("|")).trim();
				  Log.d("*:updateShow", "Title: " + epTitle);
				  
				  //Description
				  while(!("description".equals(xpp.getName()) && eventType == XmlPullParser.START_TAG))
				  {
					  eventType = xpp.next();
					  if(eventType==XmlPullParser.END_DOCUMENT)
						  throw(new UnfinishedParseException("Description"));
				  }
				  eventType = xpp.next();
				  epDesc = xpp.getText();
				  if(epDesc==null)
					  throw(new UnfinishedParseException("Description"));
				  Log.d("*:updateShow", "Desc: " + epDesc);
				  
				  //Date
				  while(!("pubDate".equals(xpp.getName()) && eventType == XmlPullParser.START_TAG))
				  {
					  eventType = xpp.next();
					  if(eventType==XmlPullParser.END_DOCUMENT)
						  throw(new UnfinishedParseException("Date"));
				  }
				  eventType = xpp.next();
				  epDate = xpp.getText();
				  Log.d("*:updateShow", "Date: " + epDate);
				  
				  
				  
				  if(epDate==null)
					  throw(new UnfinishedParseException("Date"));
				  if(lastChecked!=null && !Callisto.sdfSource.parse(epDate).after(Callisto.sdfSource.parse(lastChecked)))
					  break;
				  if(newLastChecked==null)
					  newLastChecked = epDate;
	
				  //Media link and size
				  while(!("enclosure".equals(xpp.getName()) && eventType == XmlPullParser.START_TAG))
				  {
					  eventType = xpp.next();
					  if(eventType==XmlPullParser.END_DOCUMENT)
						  throw(new UnfinishedParseException("Media"));
				  }
				  
				  String epMediaLink = xpp.getAttributeValue(xpp.getNamespace(),"url");
				  if(epMediaLink==null)
					  throw(new UnfinishedParseException("MediaLink"));
				  
				  String temp = xpp.getAttributeValue(xpp.getNamespace(),"length");
				  if(temp==null)
					  throw(new UnfinishedParseException("MediaSize"));
				  long epMediaSize = Long.parseLong(temp);
				  
				  Log.d("*:updateShow", "Link: " + epMediaLink);
				  Log.d("*:updateShow", "Size: " + epMediaSize);
				  
				  
				  epDate = Callisto.sdfRaw.format(Callisto.sdfSource.parse(epDate));
		    	  Callisto.databaseConnector.insertEpisode(AllShows.SHOW_LIST[currentShow], epTitle, epDate, epDesc, epMediaLink, epMediaSize, isVideo);
		    	  Log.v("*:updateShow", "Inserting episode: " + epTitle);
  		  }
  		  
	   } catch (XmlPullParserException e) {
		   Log.e("*:update:XmlPullParserException", "Parser error");
		   //TODO EXCEPTION: XmlPullParserException
		   e.printStackTrace();
	   } catch (MalformedURLException e) {
		   Log.e("*:update:MalformedURLException", "Malformed URL? That should never happen.");
		   e.printStackTrace();
	   } catch (IOException e) {
		   //FIXME: EXCEPTION: IOException
		   Log.e("*:update:IOException", "IO is a moon");
			e.printStackTrace();
	   } catch (ParseException e) {
		   //FIXME: EXCEPTION: ParseException
		   Log.e("*:update:ParseException", "Date Parser error: |" + epDate + "|");
	   } catch (UnfinishedParseException e) {
		   Log.w("*:update:UnfinishedParseException",e.toString());
	   }
	   	
	   	  
	   Message m = new Message();
 	   if(newLastChecked==null)
 	   {
 		   Log.v("*:updateShow", "Not updating lastChecked: " + newLastChecked);
 		   m.arg1=0;
 	   }
 	   else
 	   {
 		Log.v("*:updateShow", "Updating lastChecked for:" + AllShows.SHOW_LIST[currentShow] + "| " + newLastChecked);
 		   SharedPreferences.Editor editor = showSettings.edit();
 		   editor.putString("last_checked", newLastChecked);
 		   editor.commit();
 		   m.arg1=1;
 	   }
 	   Log.i("*:updateShow", "Finished update");
 	   return m;
	}
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    //Everything below this line is either vastly incomplete or for debugging
    //-------------------------
    
    
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	menu.add(0, QUIT_ID, 0, RESOURCES.getString(R.string.quit)).setIcon(R.drawable.ic_menu_close_clear_cancel);
    	menu.add(SETTINGS_ID, SETTINGS_ID, 0, RESOURCES.getString(R.string.settings)).setIcon(R.drawable.ic_menu_preferences);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
 
        switch (item.getItemId())
        {
        case QUIT_ID:
        	finish();	//FEATURE: Completely quit
            return true;
        case SETTINGS_ID:
        	startActivity(new Intent(this, QuickPrefsActivity.class));
        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public class oOnPreparedListener implements OnPreparedListener
    {
    	Context c;
    	public ProgressDialog pd = null;

    	public void setContext(Context c)
    	{
    		this.c = c;
    	}

		@Override
		public void onPrepared(MediaPlayer arg0) {
			Log.i("*:playTrack", "Prepared, seeking to " + Callisto.playerInfo.position);
			Callisto.mplayer.seekTo(Callisto.playerInfo.position);
			Callisto.playerInfo.length = Callisto.mplayer.getDuration()/1000;
			if(pd!=null)
				pd.cancel();
	    	((ImageButton)((Activity)c).findViewById(R.id.playPause)).setImageDrawable(Callisto.pauseDrawable);
	    	Log.i("*:playTrack", "Starting to play: " + Callisto.playerInfo.title);
			Callisto.mplayer.start();
			Callisto.playerInfo.isPaused = false;
			Callisto.playerInfo.update(c);
			pd=null;
		}
    	
    }
    
    public class oOnCompletionListener implements OnCompletionListener
    {
    	Context c;

    	public void setContext(Context c)
    	{
    		this.c = c;
    	}
    	
		@Override
		public void onCompletion(MediaPlayer mp) {
			Log.i("*:player:onCompletion", "Playing next track");
			Callisto.playTrack(this.c, 1, true);
		}
    	
    }
    
    public class oOnErrorListener implements OnErrorListener
    {
    	Context c;

    	public void setContext(Context c)
    	{
    		this.c = c;
    	}
    	

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			System.out.println("Next Track Bug");
			//Callisto.playTrack(this.c);
			return true;
		}
    	
    }
}