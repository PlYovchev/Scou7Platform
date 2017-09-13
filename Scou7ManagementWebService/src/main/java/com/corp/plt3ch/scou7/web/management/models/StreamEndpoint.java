package com.corp.plt3ch.scou7.web.management.models;

public class StreamEndpoint {
   
   private int port;
   private StreamInfo streamInfo;
   
   public int getPort() {
      return port;
   }
   public void setPort(int port) {
      this.port = port;
   }
   
   public StreamInfo getStreamInfo() {
      return streamInfo;
   }
   public void setStreamInfo(StreamInfo streamInfo) {
      this.streamInfo = streamInfo;
   }
}
