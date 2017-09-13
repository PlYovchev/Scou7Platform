package com.corp.plt3ch.scou7.web.management.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class StreamInfo {

   private GeoLocation location;
   private String uniqueId;
   private StreamState streamState;
   private long lastUpdatedTimestamp;
   
   public GeoLocation getLocation() {
      return location;
   }
   public void setLocation(GeoLocation location) {
      this.location = location;
   }
   
   public String getUniqueId() {
      return uniqueId;
   }
   public void setUniqueId(String uniqueId) {
      this.uniqueId = uniqueId;
   }
   
   public StreamState getStreamState() {
      return streamState;
   }
   
   public void setStreamState(StreamState streamState) {
      this.streamState = streamState;
   }
   
   @JsonIgnore
   public long getLastUpdatedTimestamp() {
      return lastUpdatedTimestamp;
   }
   public void setLastUpdatedTimestamp(long lastUpdatedTimestamp) {
      this.lastUpdatedTimestamp = lastUpdatedTimestamp;
   }
}
