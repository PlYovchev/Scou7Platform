package com.corp.plt3ch.scou7.web.management.data;

import java.util.concurrent.ConcurrentHashMap;

import com.corp.plt3ch.scou7.web.management.models.StreamEndpoint;
import com.corp.plt3ch.scou7.web.management.models.StreamInfo;

public class InMemoryStreamsData implements IStreamsData {

   private ConcurrentHashMap<String, StreamEndpoint> _streamsEndpointsById = null;
   private StreamsDataSanitizer _sanitizer = null;
   
   public InMemoryStreamsData() {
      _streamsEndpointsById = new ConcurrentHashMap<String, StreamEndpoint>();
   }
  

   @Override
   public void getStreamEndpoint(String id) {
      
   }

   @Override
   public void updateStreamInfo(String id, StreamInfo streamInfo) {
      
   }

   @Override
   public void updateStreamEndpoint(String id, StreamEndpoint streamEndpoint) {
      
   }
   
   

}
