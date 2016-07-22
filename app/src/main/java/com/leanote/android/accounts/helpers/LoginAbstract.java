package com.leanote.android.accounts.helpers;

/**
 * Created by binnchx on 9/7/15.
 */
public abstract class LoginAbstract {
    protected String mUsername;
    protected String mPassword;
    protected Callback mCallback;

    public interface Callback {
        void onSuccess();

        void onError();
    }

    public LoginAbstract(String username, String password) {
        mUsername = username;
        mPassword = password;
    }

    public void execute(Callback callback) {
        mCallback = callback;
        login();
    }

    protected abstract void login();
}