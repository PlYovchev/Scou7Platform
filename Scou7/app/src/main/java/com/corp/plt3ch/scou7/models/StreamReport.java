package com.corp.plt3ch.scou7.models;

public class StreamReport {

   private boolean streamInfoUpdated;
   private boolean watchingStreamOn;

   public boolean isStreamInfoUpdated() {
      return streamInfoUpdated;
   }
   public void setStreamInfoUpdated(boolean streamInfoUpdated) {
      this.streamInfoUpdated = streamInfoUpdated;
   }

   public boolean isWatchingStreamOn() {
      return watchingStreamOn;
   }
   public void setWatchingStreamOn(boolean watchingStreamOn) {
      this.watchingStreamOn = watchingStreamOn;
   }

   public static StreamReport negativeStreamReport() {
      StreamReport streamReport = new StreamReport();
      streamReport.setStreamInfoUpdated(false);
      streamReport.setWatchingStreamOn(false);
      return streamReport;
   }
}
