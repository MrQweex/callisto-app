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
package com.qweex.callisto;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.database.Cursor;
import android.preference.*;

import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;


/** The activity that allows the user to change the preferences.
 * @author MrQweex
 */

public class QuickPrefsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener
{
    /** Donation app id for Qweex */
	public final static String DONATION_APP = "com.qweex.donation";

    /** Used to determine if the livestream should be stopped and started */
	private String oldRadioForLiveQuality;
    /** A button to do the play/pausing; essentially calls methods already in place rather than trying to do it from scratch. */
	private ImageButton MagicButtonThatDoesAbsolutelyNothing;	//This has to be an imagebutton because it is defined as such in Callisto.playPause

    private AlertDialog customFeedDialog, newCustomFeedDialog;
    private LinearLayout customFeedDialogView;
    private EditText newCustomFeed;

    private Preference custom_feeds, irc_settings;

	/** Called when the activity is created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if(android.os.Build.VERSION.SDK_INT >= 11)
            setTheme(R.style.Default_New);
        super.onCreate(savedInstanceState);
        //Deprecated functions ftw!
        addPreferencesFromResource(R.xml.preferences);
        findPreference("irc_max_scrollback").setOnPreferenceChangeListener(numberCheckListener);    //Set listener for assuring that it is just a number
        irc_settings = this.getPreferenceScreen().findPreference("irc_settings");
        custom_feeds = this.getPreferenceScreen().findPreference("custom_feeds");
        //findPreference("secret").setEnabled(packageExists(DONATION_APP, this));                     //Enable secret features if donation app exists

        oldRadioForLiveQuality = PreferenceManager.getDefaultSharedPreferences(this).getString("live_url", "callisto");
        MagicButtonThatDoesAbsolutelyNothing = new ImageButton(this);
        MagicButtonThatDoesAbsolutelyNothing.setOnClickListener(PlayerControls.playPauseListener);

        irc_settings.setOnPreferenceClickListener(setSubpreferenceBG);
        custom_feeds.setOnPreferenceClickListener(setSubpreferenceBG);

        loadCustom();

        Log.i("Derp", "In constructor after loadCustom");

        //Show a dialog when the user presses the reset IRC colors
        this.getPreferenceScreen().findPreference("reset_colors").setOnPreferenceClickListener(new OnPreferenceClickListener()
        {
			@Override
			public boolean onPreferenceClick(Preference preference) {
		       	 DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		       		    @Override
		       		    public void onClick(DialogInterface dialog, int which) {
		       		        switch (which){
		       		        case DialogInterface.BUTTON_POSITIVE:
		       		            //Yes button clicked
		       		        	String[] keys = {"text", "back", "topic", "mynick", "me", "links", "pm", "join", "nick", "part", "quit", "kick", "mention", "error"};
		       		        	SharedPreferences.Editor e = PreferenceManager.getDefaultSharedPreferences(QuickPrefsActivity.this).edit();
		       		        	for(String k : keys)
		       		        		e.remove("irc_color_" + k);
		       		        	e.commit();
		       		        	finish();
		       		        	android.content.Intent i = new android.content.Intent(QuickPrefsActivity.this, QuickPrefsActivity.class);
		       		        	startActivity(i);
		       		            break;

		       		        case DialogInterface.BUTTON_NEGATIVE:
		       		            //No button clicked
		       		            break;
		       		        }
		       		    }
		       		};

		       		AlertDialog.Builder builder = new AlertDialog.Builder(preference.getContext());
		       		builder.setTitle(R.string.reset_colors_title);
		       		builder.setMessage(R.string.reset_colors_message);
		       		builder.setPositiveButton(android.R.string.yes, dialogClickListener);
		       		builder.setNegativeButton(android.R.string.no, dialogClickListener);
		       		builder.show();
		       		return true;
			}
             });
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        //Create dialog for custom feeds
        customFeedDialogView = (LinearLayout) QuickPrefsActivity.this.getLayoutInflater().inflate(R.layout.custom_feed_dialog, null);
        customFeedDialogView.findViewById(R.id.delete).setOnClickListener(removeShow);
        customFeedDialog = new AlertDialog.Builder(this)
                .setView(customFeedDialogView)
                .setPositiveButton(android.R.string.yes, customFeedDialogConfirm)
                .setNegativeButton(android.R.string.no, null).create();

        //New dialog for custom feeds
        newCustomFeed = new EditText(this);
        newCustomFeed.setHint(R.string.rss_url);
        newCustomFeedDialog = new AlertDialog.Builder(this)
                .setView(newCustomFeed)
                .setTitle(R.string.new_)
                .setPositiveButton(android.R.string.yes, addCustomFeed)
                .setNegativeButton(android.R.string.no, null).create();
    }

    @Override
    public void onContentChanged()
    {
        super.onContentChanged();
        setSubpreferenceBG.onPreferenceClick(custom_feeds);
    }

    /** Sets the preference background color for theming. http://stackoverflow.com/a/3223676/1526210 */
    OnPreferenceClickListener setSubpreferenceBG = new OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            try {
            PreferenceScreen a = (PreferenceScreen) preference;
            a.getDialog().getWindow().setBackgroundDrawableResource(R.color.backClr);
            } catch(Exception e){
                Log.i("Derp", "preference click ERROR " + e.getCause());
            }
            return false;
        }
    };

    /** Called when any of the preferences is changed. Used to perform actions on certain events. */
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        String TAG = StaticBlob.TAG();
        //Move files to new storage_path
    	if("storage_path".equals(key))
    	{
    		//Move folder for storage dir
	    	String new_path = sharedPreferences.getString("storage_path", "callisto");
            if(!new_path.startsWith("/"))
                new_path = Environment.getExternalStorageDirectory().toString() + File.separator + new_path;
	    	if(new_path.equals(StaticBlob.storage_path))
	    		return;
	    	File newfile = new File(new_path);
	    	newfile.mkdirs();
	    	
	    	File olddir = new File(StaticBlob.storage_path);
	    	if(!olddir.renameTo(new File(new_path)))
	    	{
	    		Toast.makeText(this, R.string.move_folder_error, Toast.LENGTH_SHORT).show();
	    		Log.e(TAG, "Oh crap, a file couldn't be moved");
	    	}
		    StaticBlob.storage_path = new_path;
    	}
        //Restart stream if live quality changes
    	else if("live_url".equals(key) && StaticBlob.live_isPlaying)
    	{
    		String new_radio = PreferenceManager.getDefaultSharedPreferences(this).getString("live_url", "callisto");
    		if(!new_radio.equals(oldRadioForLiveQuality))
    		{
				MagicButtonThatDoesAbsolutelyNothing.performClick();
				MagicButtonThatDoesAbsolutelyNothing.performClick();
    			oldRadioForLiveQuality =new_radio;
    		}
    	}
        //Change the IRC scrollback
    	else if(key.equals("irc_max_scrollback"))
        {
            StaticBlob.ircChat.setMaximumCapacity(PreferenceManager.getDefaultSharedPreferences(this).getInt("irc_max_scrollback", 500));
            StaticBlob.ircLog.setMaximumCapacity(PreferenceManager.getDefaultSharedPreferences(this).getInt("irc_max_scrollback", 500));
        }
        else if(key.equals("hide_notification_when_paused") && StaticBlob.mplayer!=null && StaticBlob.playerInfo.isPaused)
        {
            if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("hide_notification_when_paused", false))
                StaticBlob.mNotificationManager.cancel(StaticBlob.NOTIFICATION_ID);
            else
            {
                long id = -1;
                try {
                    Cursor c = StaticBlob.databaseConnector.currentQueueItem();
                    c.moveToFirst();
                    id = c.getLong(c.getColumnIndex("identity"));
                }catch(Exception e){}
                PlayerControls.createNotification(this, id);
            }
        }

    }
    
    /** Determines if a package (i.e. application) is installed on the device.
     * http://stackoverflow.com/questions/6758841/how-to-know-perticular-package-application-exist-in-the-device
     * @param targetPackage The target package to test
     * @param c The Context of the where to search? I dunno.
     * @return True of the package is installed, false otherwise
     */
    public static boolean packageExists(String targetPackage, Context c)
    {
	   PackageManager pm=c.getPackageManager();
	   try {
		   pm.getPackageInfo(targetPackage,PackageManager.GET_META_DATA);
	       } catch (NameNotFoundException e) {
	    	   return false;
	    }  
	    return true;
   }
    
    /** Checks to make sure that a number (in this case, the scrollback) is a legit integer */
    //http://stackoverflow.com/questions/3206765/number-preferences-in-preference-activity-in-android
    Preference.OnPreferenceChangeListener numberCheckListener = new OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
        	return (!newValue.toString().equals("")  &&  newValue.toString().matches("\\d*")); 
        }
    };

    public void loadCustom() {
        String TAG = StaticBlob.TAG();
        setSubpreferenceBG.onPreferenceClick(custom_feeds);

        PreferenceScreen ps;
        if(custom_feeds==null)
            return;
        ps = (PreferenceScreen) custom_feeds;
        ps.removeAll();

        //Add the custom feeds
        Cursor c = StaticBlob.databaseConnector.getCustomFeeds();
        if(c.getCount()>0)
        {
            c.moveToFirst();
            do {
                Preference newPref = new Preference(custom_feeds.getContext());
                newPref.setTitle(c.getString(c.getColumnIndex("title")));
                newPref.setKey(c.getString(c.getColumnIndex("_id")));      //TODO Is this creating junk keys???
                newPref.setOnPreferenceClickListener(customFeedListener);
                ps.addItemFromInflater(newPref);
            } while(c.moveToNext());
        }
        PreferenceCategory cat = new PreferenceCategory(custom_feeds.getContext());
        ps.addItemFromInflater(cat);
        Preference newPref = new Preference(custom_feeds.getContext());
        newPref.setTitle(R.string.new_);
        newPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                newCustomFeedDialog.show();
                return false;
            }
        });
        cat.addItemFromInflater(newPref);
    }

    /** Click custom feed */
    Preference.OnPreferenceClickListener customFeedListener = new OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference p) {
            String TAG = StaticBlob.TAG();

            Cursor c = StaticBlob.databaseConnector.getCustomFeed(Long.parseLong(p.getKey()));
            if(c.getCount()==0)
                return false;

            c.moveToFirst();

            ((EditText) customFeedDialogView.findViewById(R.id.title)).setTag(p.getKey());
            ((EditText) customFeedDialogView.findViewById(R.id.title)).setText(p.getTitle());
            ((EditText) customFeedDialogView.findViewById(R.id.rssurl)).setText(c.getString(c.getColumnIndex("url")));

            customFeedDialog.setTitle(p.getTitle());
            customFeedDialog.show();
            return false;
        }
    };

    /** Modifies an existing feed */
    DialogInterface.OnClickListener customFeedDialogConfirm = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {

            long id = Long.parseLong((String) customFeedDialog.findViewById(R.id.title).getTag());
            String title = ((EditText) customFeedDialog.findViewById(R.id.title)).getText().toString(),
                    url = ((EditText) customFeedDialog.findViewById(R.id.rssurl)).getText().toString();

            //Check to make sure it ain't empty
            if(title.trim().length()==0)
                return;
            StaticBlob.databaseConnector.updateCustomFeed(id, title, url);
            loadCustom();
        }
    };

    /** Confirms adding custom feed */
    DialogInterface.OnClickListener addCustomFeed = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            String url = newCustomFeed.getText().toString();
            String title;
            try {
                title = getPageTitle(new URL(url));
            } catch(Exception e) {
                Toast.makeText(QuickPrefsActivity.this, "ERROR: " + e.getMessage(), Toast.LENGTH_LONG).show();
                title = "New Feed";
            }
            StaticBlob.databaseConnector.addCustomFeed(title, url);
            loadCustom();
        }
    };

    View.OnClickListener removeShow = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            AlertDialog d = new AlertDialog.Builder(view.getContext())
                    .setTitle(R.string.confirm)
                    .setMessage(R.string.confirm_clear)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String title = ((TextView) customFeedDialog.findViewById(R.id.title)).getText().toString();
                            SharedPreferences.Editor editor = getSharedPreferences(title, 0).edit();
                            editor.remove("last_checked");
                            editor.commit();

                            long id = Long.parseLong((String) customFeedDialog.findViewById(R.id.title).getTag());
                            StaticBlob.databaseConnector.removeCustomFeed(id);

                            loadCustom();
                            customFeedDialog.dismiss();
                        }})
                    .setNegativeButton(android.R.string.no, null).show();
            StaticBlob.formatAlertDialogButtons(d);
        }
    };


    String getPageTitle(URL url) throws Exception
    {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(url.openStream()));

        Pattern pHead = Pattern.compile("(?i)</HEAD>");
        Matcher mHead;
        Pattern pTitle = Pattern.compile("(?i)</TITLE>");
        Matcher mTitle;

        String inputLine;
        boolean found=false;
        boolean notFound=false;
        String html = "";
        String title=new String();
        try{
            while (!(((inputLine = in.readLine()) == null) || found || notFound)){
                html=html+inputLine;
                mHead=pHead.matcher(inputLine);
                if(mHead.find()){
                    notFound=true;
                }
                else{
                    mTitle=pTitle.matcher(inputLine);
                    if(mTitle.find()){
                        found=true;
                    }
                }
            }
            in.close();

            html = html.replaceAll("\\s+", " ");
            if(found){
                Pattern p = Pattern.compile("(?i)<TITLE.*?>(.*?)</TITLE>");
                Matcher m = p.matcher(html);
                while (m.find() == true) {
                    title=m.group(1);
                }
            }
        }catch(Exception e){
        }
        return title;
    }
}