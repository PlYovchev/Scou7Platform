package com.corp.plt3ch.scou7.controllers;

import com.corp.plt3ch.scou7.listeners.StreamingRequestErrorListener;
import com.corp.plt3ch.scou7.models.GeoLocation;
import com.corp.plt3ch.scou7.models.StreamEndpoint;
import com.corp.plt3ch.scou7.models.StreamInfo;
import com.corp.plt3ch.scou7.models.StreamReport;

public interface StreamingManagementController {
   StreamEndpoint requestStreamCreation(GeoLocation location,
         StreamingRequestErrorListener errorListener);

   StreamEndpoint requestNextStreamEndpoint(StreamInfo streamInfo,
         StreamingRequestErrorListener errorListener);

   StreamReport requestUpdateOwnStreamInfo(StreamInfo streamInfo,
         StreamingRequestErrorListener errorListener);
}
