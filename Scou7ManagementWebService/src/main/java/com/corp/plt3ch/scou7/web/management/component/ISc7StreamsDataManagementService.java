package com.corp.plt3ch.scou7.web.management.component;

import com.corp.plt3ch.scou7.web.management.models.GeoLocation;
import com.corp.plt3ch.scou7.web.management.models.StreamEndpoint;
import com.corp.plt3ch.scou7.web.management.models.StreamInfo;
import com.corp.plt3ch.scou7.web.management.models.StreamReport;

public interface ISc7StreamsDataManagementService {
   
   StreamEndpoint createNewStreamEndpoint(GeoLocation location);
   
   int getAvailableStreamsCount(GeoLocation location);
   
   StreamEndpoint getNextStreamEndpoint(StreamInfo streamInfo);
   
   StreamReport updateStreamInfo(StreamInfo streamInfo);
}
