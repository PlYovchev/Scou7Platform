package com.corp.plt3ch.scou7.web.management.component;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.corp.plt3ch.scou7.web.management.models.GeoLocation;
import com.corp.plt3ch.scou7.web.management.models.StreamEndpoint;
import com.corp.plt3ch.scou7.web.management.models.StreamInfo;
import com.corp.plt3ch.scou7.web.management.models.StreamReport;
import com.corp.plt3ch.scou7.web.management.models.StreamState;
import com.corp.plt3ch.scou7.web.management.utils.LocationOperationsUtil;

@Component
public class Sc7StreamsDataManagementService implements ISc7StreamsDataManagementService {
   
   private static final Logger logger = Logger.getLogger(Sc7StreamsDataManagementService.class);
   
   private static final long MAX_KEEP_ALIVE_SECONDS_WITHOUT_UPDATE = 10;
   private static final float ALLOWED_DISTANCE_BETWEEN_STEAMS_IN_METERS = 100.0f;
   private static final int SANITIZATION_PERIOD = 10;

   final private ConcurrentHashMap<String, StreamEndpoint> _streamEndpointsMap;
   final private Map<String, String> _streamIdToWatchedStreamId;
   final private Map<String, String> _streamWatchedIdToStreamId; 
   final private StreamsDataSanitizer _sStreamsDataSanitizer;
   
   public Sc7StreamsDataManagementService() {
      _streamEndpointsMap = new ConcurrentHashMap<String, StreamEndpoint>();
      _streamIdToWatchedStreamId = new HashMap<String, String>();
      _streamWatchedIdToStreamId = new HashMap<String, String>();
      
      _sStreamsDataSanitizer = new StreamsDataSanitizer();
      _sStreamsDataSanitizer.startSanitizer(SANITIZATION_PERIOD);
   }
   
   @Override
   public StreamEndpoint createNewStreamEndpoint(GeoLocation location) {
      int port;
      try {
         port = this.getAvailablePort();
      } catch (IOException e) {
         e.printStackTrace();
         return null;
      }
      
      String uniqueId = UUID.randomUUID().toString();
      long timestamp = System.currentTimeMillis();
      
      StreamInfo streamInfo = new StreamInfo();
      streamInfo.setUniqueId(uniqueId);
      streamInfo.setLocation(location);
      streamInfo.setStreamState(StreamState.STOPPED);
      streamInfo.setLastUpdatedTimestamp(timestamp);
      
      StreamEndpoint endpoint = new StreamEndpoint();
      endpoint.setPort(port);
      endpoint.setStreamInfo(streamInfo);
      
      _streamEndpointsMap.putIfAbsent(uniqueId, endpoint);
      
      return endpoint;
   }

   @Override
   public int getAvailableStreamsCount(GeoLocation location) {
      int count = 0;
      long timestamp = System.currentTimeMillis();
      for (Entry<String, StreamEndpoint> entry : _streamEndpointsMap.entrySet()) {
         StreamEndpoint endpoint = entry.getValue();
         if (shouldStreamBeConsideredAlived(timestamp, endpoint)
               && areLocationsInAllowedRange(location, 
                     endpoint.getStreamInfo().getLocation())
               && checkStreamState(StreamState.PLAYING, endpoint)) {
            count++;
         }
      }
      return count;
   }

   @Override
   public StreamEndpoint getNextStreamEndpoint(StreamInfo streamInfo) {
      long timestamp = System.currentTimeMillis();
      List<StreamEndpoint> nearByEndpoints = new ArrayList<StreamEndpoint>();
      for (Entry<String, StreamEndpoint> entry : _streamEndpointsMap.entrySet()) {
         StreamEndpoint endpoint = entry.getValue();
         if (shouldStreamBeConsideredAlived(timestamp, endpoint) 
               && areLocationsInAllowedRange(streamInfo.getLocation(), 
                   endpoint.getStreamInfo().getLocation())
               && !streamInfo.getUniqueId().equals(entry.getKey())
               && !hasRecentlyWatchedStreamEndpoint(endpoint, streamInfo.getUniqueId())
               && checkStreamState(StreamState.PLAYING, endpoint)) {
            nearByEndpoints.add(endpoint);
         }
      }
      
      if (nearByEndpoints.isEmpty()) {
         return null;
      }
      
      Random random = new Random();
      int randomEndpointIndex = random.nextInt(nearByEndpoints.size());
      StreamEndpoint nextStreamEndpoint = nearByEndpoints.get(randomEndpointIndex);
      _streamIdToWatchedStreamId.put(streamInfo.getUniqueId(), 
            nextStreamEndpoint.getStreamInfo().getUniqueId());
      _streamWatchedIdToStreamId.put(nextStreamEndpoint.getStreamInfo().getUniqueId(),
            streamInfo.getUniqueId());
      return nextStreamEndpoint;
   }

   @Override
   public StreamReport updateStreamInfo(StreamInfo streamInfo) {
      long timestamp = System.currentTimeMillis();
      streamInfo.setLastUpdatedTimestamp(timestamp);
      StreamEndpoint endpoint = _streamEndpointsMap.get(streamInfo.getUniqueId());
      if (endpoint == null) {
         StreamReport.negativeStreamReport();
      }
      
      StreamReport report = new StreamReport();
      endpoint.setStreamInfo(streamInfo);
      _streamEndpointsMap.putIfAbsent(streamInfo.getUniqueId(), endpoint);
      
      if (!checkStreamState(StreamState.PLAYING, endpoint)) {
         String watchingStreamId = _streamWatchedIdToStreamId.get(streamInfo.getUniqueId());
         _streamWatchedIdToStreamId.remove(streamInfo.getUniqueId());
         _streamIdToWatchedStreamId.remove(watchingStreamId);
      }
      
      report.setStreamInfoUpdated(true);
      report.setWatchingStreamOn(checkIfWatchingStreamIsOn(streamInfo.getUniqueId()));
      return report;
   }
   
   private int getAvailablePort() throws IOException {
      ServerSocket socket = new ServerSocket(0);
      int port = socket.getLocalPort();
      socket.close();
      return port;
   }
  
   private boolean shouldStreamBeConsideredAlived(long currentTimeMillis, StreamEndpoint endpoint) {
      long timestampDiffInSeconds = 
            (currentTimeMillis - endpoint.getStreamInfo().getLastUpdatedTimestamp()) / 1000;
      logger.info(timestampDiffInSeconds);
      return timestampDiffInSeconds < MAX_KEEP_ALIVE_SECONDS_WITHOUT_UPDATE;
   }
   
   private boolean areLocationsInAllowedRange(GeoLocation requestingLocation, GeoLocation sourceLocation) {
      float distanceBetweenStreams = LocationOperationsUtil.distFrom(
            requestingLocation.getLatitude(), 
            requestingLocation.getLongitude(), 
            sourceLocation.getLatitude(), 
            sourceLocation.getLongitude());
      return distanceBetweenStreams < ALLOWED_DISTANCE_BETWEEN_STEAMS_IN_METERS;
   }
   
   private boolean checkStreamState(StreamState streamState, StreamEndpoint endpoint) {
      return streamState.equals(endpoint.getStreamInfo().getStreamState());
   }
   
   private boolean hasRecentlyWatchedStreamEndpoint(StreamEndpoint streamEndpoint, String streamId) {
      String lastWatchedStreamId = _streamIdToWatchedStreamId.get(streamId);
      if (lastWatchedStreamId == null) {
         return false;
      }
      StreamEndpoint lastWatchedStream = _streamEndpointsMap.get(lastWatchedStreamId);
      if (lastWatchedStream == null) {
         return false;
      }
      
      return lastWatchedStream.getStreamInfo().getUniqueId()
            .equals(streamEndpoint.getStreamInfo().getUniqueId());
   }
   
   private boolean checkIfWatchingStreamIsOn(String streamId) {
      String lastWatchedStreamId = _streamIdToWatchedStreamId.get(streamId);
      if (lastWatchedStreamId == null) {
         return false;
      }
      StreamEndpoint lastWatchedStream = _streamEndpointsMap.get(lastWatchedStreamId);
      if (lastWatchedStream == null) {
         return false;
      }
      
      return checkStreamState(StreamState.PLAYING, lastWatchedStream);
   }
   
   private class StreamsDataSanitizer {
      
      private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
      
      ScheduledFuture<?> sanitizerHandle = null;
      
      void startSanitizer(int executePerPeriod) {
         Runnable sanitizeData = new Runnable() {
            
            @Override
            public void run() {
               removeDestoyedAndNotUpdatedStreams();
            }
         };
         
         sanitizerHandle = scheduler.scheduleAtFixedRate(
               sanitizeData, 0, executePerPeriod, TimeUnit.SECONDS);
      }
      
      void stopSanitizer() {
         if (sanitizerHandle != null) {
            sanitizerHandle.cancel(false);
         }
      }
      
      void removeDestoyedAndNotUpdatedStreams() {
         long timestamp = System.currentTimeMillis();
         for (Entry<String, StreamEndpoint> entry : _streamEndpointsMap.entrySet()) {
            StreamEndpoint endpoint = entry.getValue();
            if (!shouldStreamBeConsideredAlived(timestamp, endpoint)
                  || checkStreamState(StreamState.DESTOYED, endpoint)) {
               _streamEndpointsMap.remove(entry.getKey());
            }
         }
      }
   }
}
