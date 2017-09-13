package com.corp.plt3ch.scou7.controllers;

import android.content.Context;

import com.corp.plt3ch.scou7.models.GeoLocation;
import com.corp.plt3ch.scou7.models.StreamEndpoint;
import com.corp.plt3ch.scou7.models.StreamState;

public class Sc7SteamingManagementController {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 7;

    private static Sc7SteamingManagementController _instance;

    public synchronized static Sc7SteamingManagementController getSc7SteamingManagementController(
            Context context) {
        if (_instance == null) {
            _instance = new Sc7SteamingManagementController(context);
        }

        return _instance;
    }

    private StreamingManagementController _streamingManagementController;
    private Sc7LocationManager _sc7locationManager;

    private StreamEndpoint _ownStreamEndpoint;
    private StreamEndpoint _currentNextStreamEndpoint;

    private Sc7SteamingManagementController(Context context) {
        _streamingManagementController = StreamingManagementControllerFactory
                .getSteamingManagementController(context);

        _sc7locationManager = Sc7LocationManager.getSc7LocationManager(context);
        _sc7locationManager.startListeningForLocationChanges();
    }

    public boolean createOwnStreamEndpoint() {
        GeoLocation lastLocation = _sc7locationManager.getLastLocation();
        _ownStreamEndpoint = _streamingManagementController.requestStreamCreation(lastLocation);
        return _ownStreamEndpoint != null;
    }

    public StreamEndpoint getOwnStreamEndpoint() {
        return _ownStreamEndpoint;
    }

    public boolean requestNextStreamEndpoint() {
        if (_ownStreamEndpoint == null) {
            return false;
        }

        updateOwnStreamInfoLocation();

        _currentNextStreamEndpoint = _streamingManagementController
                .requestNextStreamEndpoint(_ownStreamEndpoint.getStreamInfo());
        return _currentNextStreamEndpoint != null;
    }

    public StreamEndpoint getCurrentNextStreamEndpoint() {
        return _currentNextStreamEndpoint;
    }

    public void clearCurrentNextStreamEndpoint() {
        _currentNextStreamEndpoint = null;
    }

    public void updateOwnStreamInfoLocation() {
        GeoLocation lastLocation = _sc7locationManager.getLastLocation();
        if (_ownStreamEndpoint == null || lastLocation == null) {
            return;
        }

        _ownStreamEndpoint.getStreamInfo().setLocation(lastLocation);
    }

    public void updateOwnStreamState(StreamState state) {
        if (_ownStreamEndpoint == null) {
            return;
        }

        _ownStreamEndpoint.getStreamInfo().setStreamState(state);
    }

    public boolean requestUpdateOwnStreamInfo() {
        return _streamingManagementController
                .requestUpdateOwnStreamInfo(_ownStreamEndpoint.getStreamInfo());
    }
}
