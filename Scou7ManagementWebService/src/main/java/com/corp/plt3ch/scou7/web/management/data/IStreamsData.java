package com.corp.plt3ch.scou7.web.management.data;

import java.util.Map;

import com.corp.plt3ch.scou7.web.management.models.StreamEndpoint;
import com.corp.plt3ch.scou7.web.management.models.StreamInfo;

public interface IStreamsData {
   
   void getStreamEndpoint(String id);
   void updateStreamInfo(String id, StreamInfo streamInfo);
   void updateStreamEndpoint(String id, StreamEndpoint streamEndpoint);
}
