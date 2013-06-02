/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.baobab.podio;


import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

/**
 * Activity which displays login screen to the user.
 */
public class AuthActivity extends AccountAuthenticatorActivity
        implements Response.Listener<String>, Response.ErrorListener,
            OnCancelListener {
    /** The Intent flag to confirm credentials. */
    public static final String PARAM_CONFIRM_CREDENTIALS = "confirmCredentials";

    /** The Intent extra to store password. */
    public static final String PARAM_PASSWORD = "password";

    /** The Intent extra to store username. */
    public static final String PARAM_USERNAME = "username";

    /** The Intent extra to store username. */
    public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";

    /** The tag used to log to adb console. */
    private static final String TAG = AuthService.TAG;
    private AccountManager mAccountManager;

    /** Keep track of the progress dialog so we can dismiss it */
    private ProgressDialog mProgressDialog = null;

    /**
     * If set we are just checking that the user knows their credentials; this
     * doesn't cause the user's password or authToken to be changed on the
     * device.
     */
    private TextView mMessage;

    private String mPassword;

    private EditText mPasswordEdit;

    /** Was the original caller asking for an entirely new account? */
    protected boolean mRequestNewAccount = false;

    private String mUsername;

    private EditText mUsernameEdit;

    private Request<?> request;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        mAccountManager = AccountManager.get(this);
        final Intent intent = getIntent();
        mUsername = intent.getStringExtra(PARAM_USERNAME);
        mRequestNewAccount = mUsername == null;
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.activity_login);
        getWindow().setFeatureDrawableResource(
                Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_alert);
        mMessage = (TextView) findViewById(R.id.message);
        mUsernameEdit = (EditText) findViewById(R.id.username_edit);
        mPasswordEdit = (EditText) findViewById(R.id.password_edit);
        if (!TextUtils.isEmpty(mUsername)) mUsernameEdit.setText(mUsername);
        mMessage.setText(getMessage());
    }

    /**
     * Handles onClick event on the Submit button. Sends username/password to
     * the server for authentication. The button is configured to call
     * handleLogin() in the layout XML.
     *
     * @param view The Submit button for which this method is invoked
     */
    public void handleLogin(View view) {
        if (mRequestNewAccount) {
            mUsername = mUsernameEdit.getText().toString();
        }
        mPassword = mPasswordEdit.getText().toString();
        if (TextUtils.isEmpty(mUsername) || TextUtils.isEmpty(mPassword)) {
            mMessage.setText(getMessage());
        } else {
            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage(getText(R.string.ui_activity_authenticating));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setOnCancelListener(this);
            }
            mProgressDialog.show();
            
            Log.i(TAG, "AUTH");
            RequestQueue queue = Volley.newRequestQueue(this);
            request = queue.add(new StringRequest(Method.POST,
                    "https://podio.com/oauth/token",
                    AuthActivity.this, AuthActivity.this) {

                @Override
                protected Map<String, String> getParams() throws AuthFailureError {  
                    Log.i(TAG, "params");
                    Map<String, String> params = new HashMap<String, String>();  
                    params.put("grant_type", "password");  
//                    params.put("username", "florian_detig@yahoo.de");  
                    params.put("username", mUsernameEdit.getText().toString());  
                    params.put("password", mPasswordEdit.getText().toString());  
                    params.put("client_id", "dummy3");  
                    params.put("client_secret", "s4d6tg6OJ6r4dWrwFbsuas5fDfQwWETXpqt2woSHc1htbiaYute9eOoUHVLKMld6");  
                    return params;  
                };
            });
            queue.start();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        Log.i(TAG, "user cancelling authentication");
        if (request != null) {
            request.cancel();
        }
        hideProgress();
    }

    @Override
    public void onResponse(String response) {
        Log.i(TAG, "on resp " + response);
        final Account account = new Account(mUsername, AuthService.ACCOUNT_TYPE);
        try {
            JSONObject json = new JSONObject(response);
            Log.i(TAG, "auth token: " + json.getString("access_token"));
            Log.i(TAG, "refresh token: " + json.getString("refresh_token"));
            mAccountManager.addAccountExplicitly(
                    account, json.getString("access_token"), null);
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
//            ContentResolver.addPeriodicSync(account, ContactsContract.AUTHORITY,
//                    new Bundle(), 3600);
            final Intent intent = new Intent();
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
            intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, AuthService.ACCOUNT_TYPE);
            setAccountAuthenticatorResult(intent.getExtras());
            setResult(RESULT_OK, intent);
            hideProgress();
            finish();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onErrorResponse(VolleyError e) {
        Log.i(TAG, "on resp Error" + e);
        mMessage.setText(getText(R.string.login_activity_loginfail));
        hideProgress();
    }

    /**
     * Returns the message to be displayed at the top of the login dialog box.
     */
    private CharSequence getMessage() {
        getString(R.string.label);
        if (TextUtils.isEmpty(mUsername)) {
            // If no username, then we ask the user to log in using an
            // appropriate service.
            final CharSequence msg = getText(R.string.login_activity_newaccount_text);
            return msg;
        }
        if (TextUtils.isEmpty(mPassword)) {
            // We have an account but no password
            return getText(R.string.login_activity_loginfail_text_pwmissing);
        }
        return null;
    }

    /**
     * Hides the progress UI for a lengthy operation.
     */
    private void hideProgress() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}
