package com.qweex.callisto.chat;


import android.util.Log;
import com.qweex.callisto.MasterActivity;
import com.sorcix.sirc.IrcConnection;
import com.sorcix.sirc.User;

public class UserTabFragment extends TabFragment {
    String TAG = super.TAG + ":User";

    User user;

    public UserTabFragment(MasterActivity master, User user) {
        super(master);
        Log.v(TAG, "Creating User Fragment");
        this.user = user;
    }

    @Override
    void send(String msg) {
        user.sendMessage(msg);
    }
}