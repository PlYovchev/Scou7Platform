package com.corp.plt3ch.scou7.web.management.component;

import com.corp.plt3ch.scou7.web.management.models.GeoLocation;
import com.corp.plt3ch.scou7.web.management.models.StreamEndpoint;
import com.corp.plt3ch.scou7.web.management.models.StreamInfo;

public interface ISc7StreamsDataManagementService {
   
   StreamEndpoint createNewStreamEndpoint(GeoLocation location);
   
   int getAvailableStreamsCount(GeoLocation location);
   
   StreamEndpoint getNextStreamEndpoint(StreamInfo streamInfo);
   
   boolean updateStreamInfo(StreamInfo streamInfo);
}
