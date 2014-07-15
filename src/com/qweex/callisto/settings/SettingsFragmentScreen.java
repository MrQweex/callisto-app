package com.qweex.callisto.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import com.qweex.callisto.ResCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsFragmentScreen extends Preference {
    String TAG = "Callisto:settings:SettingsFragmentsScreen";

    AttributeSet myAttributes;

    List<Preference> preferences = new ArrayList<Preference>();
    Map<Preference, SettingsAttributes> prefAttributes = new HashMap<Preference, SettingsAttributes>();

    SettingsFragmentScreen parent;

    ListView listview;
    SettingsFragmentAdapter adapter;

    public SettingsFragmentScreen(Context context, AttributeSet attrs, SettingsFragmentScreen parent) {
        super(context, attrs);
        this.myAttributes = attrs;
        this.parent = parent;
    }

    public SettingsFragmentScreen addChild(Preference child, SettingsAttributes childAttrs) {
        Log.v(TAG, "Adding child: " + child.getKey() + " | " + childAttrs);
        preferences.add(child);
        prefAttributes.put(child, childAttrs);
        return this;
    }

    public List<Preference> getPreferences() { return preferences; }

    public SettingsAttributes getAttributes(Preference p) { return prefAttributes.get(p); }

    public AttributeSet getMyAttributes() { return myAttributes; }

    public SettingsFragmentScreen getParent() { return parent; }

    public ListView getListView() {
        if(listview==null) {

            Log.v(TAG, "Restoring Preference Values");
            for(Preference preference : preferences) {
                // Restore the value from the persisted state
                SettingsAttributes attrs = prefAttributes.get(preference);
                String defaultValue = attrs.get("defaultValue");
                Log.v(TAG, ":Restoring preference " + preference.getKey() + " defaultValue: " + defaultValue);


                if(preference instanceof CheckBoxPreference) {
                    boolean trueDefaultValue = defaultValue!=null && Boolean.parseBoolean(defaultValue);
                    boolean restoredValue = getSharedPreferences().getBoolean(preference.getKey(), trueDefaultValue);
                    CheckBoxPreference check = ((CheckBoxPreference) preference);
                    check.setChecked(restoredValue);
                    setDependentPreferencesEnabled(check);
                }
            }

            Log.v(TAG, "Creating ListView");
            listview = new ListView(getContext());
            listview.setBackgroundColor(ResCache.clr(com.qweex.callisto.R.color.settings_background));

            adapter = new SettingsFragmentAdapter(getContext(), this);
            listview.setAdapter(adapter);

            listview.setOnItemClickListener(onItemClickListener);
        }
        return listview;
    }



    AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if(!view.isEnabled())
                return;
            Preference preference = preferences.get(position);

            boolean eventHandled = preference.getOnPreferenceClickListener()!=null && preference.getOnPreferenceClickListener().onPreferenceClick(preference);
            if(eventHandled) return;

            if(preference.getClass() == CheckBoxPreference.class)
                setCheckPreference((CheckBoxPreference) preference, !((CheckBoxPreference) preference).isChecked(), view);
        }
    };


    void setCheckPreference(CheckBoxPreference check, boolean newValue, View view) {
        check.setChecked(newValue);

        if(view!=null)
            ((CheckBox)view.findViewById(android.R.id.checkbox)).setChecked(check.isChecked());

        Log.v(TAG, "Writing new pref value: " + check.isChecked());
        getSharedPreferences().edit().putBoolean(check.getKey(), check.isChecked()).commit();
        setDependentPreferencesEnabled(check);
    }


    // Handles dependencies
    void setDependentPreferencesEnabled(Preference pref) {

        Object value = getSharedPreferences().getAll().get(pref.getKey());
        boolean enabled = value!=null;
        if(enabled && value instanceof Boolean)
            enabled = (Boolean) value;


        setDependentPreferencesEnabled(pref, enabled);
    }

    void setDependentPreferencesEnabled(Preference pref, boolean enabled) {
        Log.v(TAG, "Setting dependency statuses of " + pref.getKey() + ": " + enabled);

        for(int i=0; i<preferences.size(); i++) {
            Preference testPref = preferences.get(i);
            if(pref.getKey().equals(testPref.getDependency())) {
                Log.v(TAG, " - Preference " + testPref.getKey() + " is a dependency");
                testPref.setEnabled(enabled);

                if(listview!=null && i>=listview.getFirstVisiblePosition() && i<=listview.getLastVisiblePosition()) {
                    setViewAndChildrenEnabled(listview.getChildAt(i), enabled);
                }

                setDependentPreferencesEnabled(testPref, enabled);
            }
        }
    }

    void setViewAndChildrenEnabled(View view, boolean enabled) {
        Log.v(TAG, " -- View " + view.getClass() + " is being set to " + enabled);
        view.setEnabled(enabled);
        if(!(view instanceof ViewGroup))
            return;

        ViewGroup viewGroup = (ViewGroup) view;
        for(int j=0; j<viewGroup.getChildCount(); j++)
            setViewAndChildrenEnabled(viewGroup.getChildAt(j), enabled);
    }

    @Override
    public SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    public static class SettingsAttributes {
        Map<String, String> attrs = new HashMap<String, String>();

        public SettingsAttributes(AttributeSet attrs) {
            for(int i=0; i<attrs.getAttributeCount(); i++) {
                this.attrs.put(
                        attrs.getAttributeName(i),
                        attrs.getAttributeValue(i)
                );
            }
        }

        public String get(String name) {
            if(attrs.containsKey(name))
                return attrs.get(name);
            return null;
        }
    }
}
