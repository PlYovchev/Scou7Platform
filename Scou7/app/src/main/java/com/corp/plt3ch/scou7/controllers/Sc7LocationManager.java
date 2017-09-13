package com.corp.plt3ch.scou7.controllers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.corp.plt3ch.scou7.models.GeoLocation;

public class Sc7LocationManager implements LocationListener {

    private static final String TAG = Sc7LocationManager.class.getSimpleName();

    private static Sc7LocationManager _instance;

    public synchronized static Sc7LocationManager getSc7LocationManager(
            Context context) {
        if (_instance == null) {
            _instance = new Sc7LocationManager(context);
        }

        return _instance;
    }

    private LocationManager _locationManager;
    private Context _context;

    private GeoLocation _lastLocation;

    private boolean _started;

    private Sc7LocationManager(Context context) {
        _context = context;
        _locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean startListeningForLocationChanges() {
        if (_started) {
            return true;
        }
        if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(_context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No access location permissions");
            return false;
        }

        _locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 10, this);
        _locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 10, this);

        Location locationGps = _locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location locationNet = _locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (locationGps != null) {
            onLocationChanged(locationGps);
        } else if (locationNet != null) {
            onLocationChanged(locationNet);
        }
        _started = true;
        return true;
    }

    public boolean stopListeningForLocationChanges() {
        if (!_started) {
            return true;
        }
        if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(_context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No access location permissions");
            return false;
        }
        _locationManager.removeUpdates(this);
        _started = false;
        return true;
    }

    public GeoLocation getLastLocation() {
        return _lastLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        GeoLocation geoLocation = new GeoLocation();
        geoLocation.setLatitude((float)location.getLatitude());
        geoLocation.setLongitude((float)location.getLongitude());
        _lastLocation = geoLocation;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
