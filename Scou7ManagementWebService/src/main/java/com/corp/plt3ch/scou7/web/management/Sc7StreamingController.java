package com.corp.plt3ch.scou7.web.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.corp.plt3ch.scou7.web.management.component.ISc7StreamsDataManagementService;
import com.corp.plt3ch.scou7.web.management.models.GeoLocation;
import com.corp.plt3ch.scou7.web.management.models.StreamEndpoint;
import com.corp.plt3ch.scou7.web.management.models.StreamInfo;

@RestController
@RequestMapping("/streaming")
public class Sc7StreamingController {
   
   @Autowired
   private ISc7StreamsDataManagementService streamsManagementService;

   @RequestMapping(value = "/register", method = RequestMethod.POST)
   public StreamEndpoint registerStream(@RequestBody GeoLocation location) {
      return streamsManagementService.createNewStreamEndpoint(location);
   }
   
   @RequestMapping("/availableStreamsCount")
   public int getAvailableStreamsCount(
         @RequestParam(value = "latitude", required = true) float latitude,
         @RequestParam(value = "longitude", required = true) float longitude) {
      GeoLocation location = new GeoLocation();
      location.setLatitude(latitude);
      location.setLongitude(longitude);
      return streamsManagementService.getAvailableStreamsCount(location);
   }
   
   @RequestMapping(value = "/nextStreamEndpoint", method = RequestMethod.POST)
   public StreamEndpoint getNextStreamEndpoint(@RequestBody StreamInfo streamInfo) {
      return streamsManagementService.getNextStreamEndpoint(streamInfo);
   }
   
   @RequestMapping(value = "/updateStreamInfo", method = RequestMethod.POST)
   public boolean updateStreamInfo(@RequestBody StreamInfo streamInfo) {
      return streamsManagementService.updateStreamInfo(streamInfo);
   }
}
