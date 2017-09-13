package com.corp.plt3ch.scou7.controllers;

import android.location.Location;

import com.corp.plt3ch.scou7.models.GeoLocation;
import com.corp.plt3ch.scou7.models.StreamEndpoint;
import com.corp.plt3ch.scou7.models.StreamInfo;

/**
 * Created by Plamen on 9/4/2017.
 */

public interface StreamingManagementController {
    StreamEndpoint requestStreamCreation(GeoLocation location);
    StreamEndpoint requestNextStreamEndpoint(StreamInfo streamInfo);
    boolean requestUpdateOwnStreamInfo(StreamInfo streamInfo);
}
