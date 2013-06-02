package org.baobab.podio;


import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Data;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;
import ch.hsr.geohash.GeoHash;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity  extends FragmentActivity
        implements LoaderCallbacks<Cursor>, OnInfoWindowClickListener, OnMapClickListener {

    private static final String TAG = "Baoliza";
    private static final String[] MIMETYPE = new String[] {
        "vnd.android.cursor.item/vnd.baobab" };
    private GoogleMap map;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_map);
    map = ((SupportMapFragment) getSupportFragmentManager()
            .findFragmentById(R.id.map)).getMap();
    map.setMyLocationEnabled(true);
    map.animateCamera(CameraUpdateFactory
            .newCameraPosition(new CameraPosition(
                    new LatLng(48.138790, 11.553338), 12, 90, 0)));
    map.setOnMapClickListener(this);
    map.setOnInfoWindowClickListener(this);
    getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
        return new CursorLoader(this, Data.CONTENT_URI, new String[] {
                Data.DATA1, Data.DATA2, Data.DATA3 },
                Data.MIMETYPE + " IS ?", MIMETYPE, null);
    }
    
    @Override
    public void onLoadFinished(Loader<Cursor> l, final Cursor cursor) {
        Log.d(TAG, "load finished: " + cursor.getCount());
        if (cursor.getCount() == 0) return;
        map.clear();
        cursor.moveToFirst();
        while (!cursor.isLast()) {
            cursor.moveToNext();
            if (cursor.getString(2) == null) Log.d(TAG, "no name");
            GeoHash gh = GeoHash
                    .fromGeohashString(
                        cursor.getString(0));
            map.addMarker(new MarkerOptions()
                    .title(cursor.getString(2))
                    .snippet(cursor.getString(1))
                    .position(new LatLng(
                           gh.getPoint().getLatitude(),
                           gh.getPoint().getLongitude())));
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Log.d(TAG, "browse " + SyncService.URL + marker.getSnippet());
        startActivity(new Intent(this, WebActivity.class)
        .setData(Uri.parse(SyncService.URL + marker.getSnippet())));
    }

    @Override
    public void onMapClick(LatLng position) {
        Toast.makeText(this, "Planted new Baobab Seed!", Toast.LENGTH_SHORT).show();
        map.addMarker(new MarkerOptions()
                .position(position)
                .icon(BitmapDescriptorFactory
                        .fromResource(R.drawable.ic_launcher))
                .draggable(true));
    }

    @Override
    public void onLoaderReset(Loader<Cursor> l) {
    }
}
