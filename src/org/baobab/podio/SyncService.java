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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.Toast;

import ch.hsr.geohash.GeoHash;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
public class SyncService extends Service {

    private static final String TAG = "PodioSync";

    public static final String URL = "http://synergie-media.de/webview/spot/view/";

    private static final Object sSyncAdapterLock = new Object();

    private static SyncAdapter sSyncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
    
    /**
     * SyncAdapter implementation for syncing sample SyncAdapter contacts to the
     * platform ContactOperations provider.  This sample shows a basic 2-way
     * sync between the client and a sample server.  It also contains an
     * example of how to update the contacts' status messages, which
     * would be useful for a messaging or social networking client.
     */
    public class SyncAdapter extends AbstractThreadedSyncAdapter
            implements Response.Listener<JSONObject>, Response.ErrorListener {

        private static final String FIELDS = "fields";

        private static final String TITLE = "title";

        private static final String ITEM_ID = "item_id";

        private static final String NIX = "nix";


        private static final String STRAßE = "Straße";

        private static final String MAILADRESSE = "Mailadresse";

        private static final String TELEFON = "Telefon";

        private static final String TEXT = "text";

        private static final String VALUE = "value";

        private static final String VALUES = "values";

        private static final String TYP = "Typ";

        private static final String LABEL = "label";

        private Account mAct;

        private String group;

        private String tel;

        private String mail;

        private String addr;

        private String name;

        private RequestQueue queue;

        private String auth;

        private int offset = 0;

        private Handler worker;

        private Geocoder geoCoder;

        private boolean isSyncing;

        private String item_id;

        public SyncAdapter(boolean autoInitialize) {
            super(getApplicationContext(), autoInitialize);
            HandlerThread thread = new HandlerThread("worker");
            thread.start();
            worker = new Handler(thread.getLooper());
            geoCoder = new Geocoder(getContext(), Locale.getDefault());
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
            if (isSyncing) {
                Log.d(TAG, "perform sync while still syncing");
                return;
            }
            AccountManager actMngr = (AccountManager) getContext()
                    .getSystemService(Context.ACCOUNT_SERVICE);
            offset = 0;
            mAct = account;
            isSyncing = true;
            getContentResolver()
                    .delete(RawContacts.CONTENT_URI
                            .buildUpon()
                            .appendQueryParameter(
                                    ContactsContract.CALLER_IS_SYNCADAPTER,
                                    "true").build(),
                            RawContacts.ACCOUNT_NAME + "=?",
                            new String[] { mAct.name });
            try {
                auth = actMngr.blockingGetAuthToken(account,
                        AuthService.AUTHTOKEN_TYPE, false);
            } catch (Exception e) {
                syncResult.stats.numAuthExceptions++;
                e.printStackTrace();
            }
            Log.d(TAG, auth);
            queue = Volley.newRequestQueue(getContext());
            loadNext();
            loadNext();
            loadNext();
            loadNext();
            try {
                Log.d(TAG, "waiting..");
                synchronized (this) {
                    this.wait();
                }
                Log.d(TAG, "..Done.");
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        private void loadNext() {
            Log.d(TAG, "queue request " + offset + "-" + (offset + 20));
            queue.add(new JsonObjectRequest("https://api.podio.com/item" +
                    "/app/4419667/?oauth_token=" + auth + "&offset=" + offset,
                    null, this, this));
            offset += 20;
        }

        @Override
        public void onResponse(JSONObject json) {
            try {
                JSONArray items = json.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    item_id = item.getString(ITEM_ID); 
                    name = item.getString(TITLE);
                    Log.d(TAG, "   + " + name);
                    JSONArray fields = item.getJSONArray(FIELDS);
                    JSONObject field;
                    for (int j = 0; j < fields.length(); j++) {
                        field = fields.getJSONObject(j);
                        if (field.getString(LABEL).equals(TYP)) {
                            group = field
                                    .getJSONArray(VALUES).getJSONObject(0)
                                    .getJSONObject(VALUE).getString(TEXT);
                        }
                        else if (field.getString(LABEL).equals(TELEFON)) {
                            tel = field.getJSONArray(VALUES)
                                    .getJSONObject(0).getString(VALUE);
                        }
                        else if (field.getString(LABEL).equals(MAILADRESSE)) {
                            mail = field.getJSONArray(VALUES)
                                    .getJSONObject(0).getString(VALUE);
                        }
                        else if (field.getString(LABEL).equals(STRAßE)) {
                            addr = field.getJSONArray(VALUES)
                                    .getJSONObject(0).getString(VALUE);
                        }
                        else if (field.getString(LABEL).equals("Stadt")) {
                            addr += (", " + field.getJSONArray(VALUES)
                                    .getJSONObject(0).getString(VALUE));
                        }
                    }
                    storeContact();
                }
                if (offset < json.getInt("total")) {
                    loadNext();
                } else {
                    isSyncing = false;
                    synchronized (this) {
                        this.notify();
                    }
                    Log.d(TAG, "syncing ready? " + queue.getSequenceNumber());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onErrorResponse(VolleyError e) {
            Toast.makeText(getContext(),
                    "Baolizer Sync Error", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Volley Error! " + e);
            isSyncing = false;
        }


        void storeContact() {
            if (name == null) {
                Log.d(TAG, " - NOT storing " + item_id);
                return;
            }
            ArrayList<ContentProviderOperation> ops
                    = new ArrayList<ContentProviderOperation>();
            ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, mAct.type)
                    .withValue(RawContacts.ACCOUNT_NAME, mAct.name)
                    .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(GroupMembership.GROUP_ROW_ID, getGroup(group))
                    .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE,
                            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE,
                            CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Organization.COMPANY, name)
                    .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE,
                            CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, addr)
                    .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE,
                            CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                     .withValue(CommonDataKinds.Website.URL, URL + item_id)
                     .withValue(CommonDataKinds.Website.LABEL, URL + item_id)
                     .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE,
                            CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, tel)
                    .withValue(CommonDataKinds.Phone.TYPE,
                            CommonDataKinds.Phone.TYPE_WORK)
                    .build());
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE,
                            CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Email.DISPLAY_NAME, mail)
                            .withValue(CommonDataKinds.Email.TYPE,
                                    CommonDataKinds.Email.TYPE_WORK)
                    .build());
            try {
                final long id = ContentUris.parseId(getContentResolver()
                        .applyBatch(ContactsContract.AUTHORITY, ops)[0].uri);
                final String it_id = item_id;
                final String address = addr;
                final String title = name;
                worker.post(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            List<Address> addresses = geoCoder
                                    .getFromLocationName(address , 1);
                            if (addresses.size() > 0) {
                                String geohash = GeoHash.withBitPrecision(
                                        addresses.get(0).getLatitude(),
                                        addresses.get(0).getLongitude(),
                                        55).toBase32();
                                ContentValues v = new ContentValues();
                                v.put(Data.RAW_CONTACT_ID, id);
                                v.put(Data.MIMETYPE, "vnd.android.cursor.item/vnd.baobab");
                                v.put(Data.DATA1, geohash);
                                v.put(Data.DATA2, it_id);
                                v.put(Data.DATA3, title);
                                getContentResolver().insert(Data.CONTENT_URI
                                        .buildUpon()
                                        .appendQueryParameter(
                                                ContactsContract.CALLER_IS_SYNCADAPTER,
                                                "true").build(), v);
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                name = null;
                addr = NIX;
                mail = NIX;
                tel = NIX;
            } catch (Exception e) {
                Log.e(TAG, "error storing " + name);
            }
        }

        public long getGroup(String name) {
            
            long groupId = 0;
            final Cursor cursor = getContentResolver().query(Groups.CONTENT_URI,
                    new String[] { Groups._ID },
                    Groups.ACCOUNT_NAME + "=? AND " +
                            Groups.ACCOUNT_TYPE + "=? AND " +
                            Groups.TITLE + "=?",
                            new String[] { mAct.name, mAct.type, name }, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        groupId = cursor.getLong(0);
                    }
                } finally {
                    cursor.close();
                }
            }
            
            if (groupId == 0) {
                // Sample group doesn't exist yet, so create it
                final ContentValues contentValues = new ContentValues();
                contentValues.put(Groups.ACCOUNT_NAME, mAct.name);
                contentValues.put(Groups.ACCOUNT_TYPE, mAct.type);
                contentValues.put(Groups.TITLE, name);
                
                final Uri newGroupUri = getContentResolver().insert(
                        Groups.CONTENT_URI, contentValues);
                groupId = ContentUris.parseId(newGroupUri);
            }
            return groupId;
        }
    }
}