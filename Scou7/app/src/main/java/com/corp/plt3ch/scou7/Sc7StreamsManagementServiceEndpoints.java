package com.corp.plt3ch.scou7;

public class Sc7StreamsManagementServiceEndpoints {

   // 192.168.1.104
   // 192.168.43.110
   public static final String SC7_MANAGEMENT_SERVICE_BASE_ENDPOINT =
         "http://192.168.1.104:8080/sc7management/streaming";
   public static final String SC7_MANAGEMENT_SERVICE_REGISTER_STREAM_ENDPOINT =
         SC7_MANAGEMENT_SERVICE_BASE_ENDPOINT + "/register";
   public static final String SC7_MANAGEMENT_SERVICE_STREAMS_COUNT_ENDPOINT =
         SC7_MANAGEMENT_SERVICE_BASE_ENDPOINT + "/availableStreamsCount";
   public static final String SC7_MANAGEMENT_SERVICE_NEXT_STREAM_ENDPOINT =
         SC7_MANAGEMENT_SERVICE_BASE_ENDPOINT + "/nextStreamEndpoint";
   public static final String SC7_MANAGEMENT_SERVICE_UPDATE_STREAM_INFO_ENDPOINT =
         SC7_MANAGEMENT_SERVICE_BASE_ENDPOINT + "/updateStreamInfo";
}
