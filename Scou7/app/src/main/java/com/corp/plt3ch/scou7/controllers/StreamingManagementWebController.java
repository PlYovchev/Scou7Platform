package com.corp.plt3ch.scou7.controllers;

import android.content.Context;
import android.location.Location;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.corp.plt3ch.scou7.Sc7StreamsManagementServiceEndpoints;
import com.corp.plt3ch.scou7.listeners.StreamingRequestErrorListener;
import com.corp.plt3ch.scou7.models.GeoLocation;
import com.corp.plt3ch.scou7.models.StreamEndpoint;
import com.corp.plt3ch.scou7.models.StreamInfo;
import com.corp.plt3ch.scou7.models.StreamReport;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public class StreamingManagementWebController implements StreamingManagementController {

   private static StreamingManagementWebController _instance = null;

   private Gson _gson;
   private Context _context;

   private StreamEndpoint _ownStreamEndpoint;

   private StreamingManagementWebController(Context context) {
      _gson = new Gson();
   }

   public static synchronized StreamingManagementWebController getInstance(Context context) {
      if (_instance == null) {
         _instance = new StreamingManagementWebController(context);
      }

      return _instance;
   }

   @Override
   public StreamEndpoint requestStreamCreation(GeoLocation location,
         StreamingRequestErrorListener errorListener) {
      if (location == null) {
         return null;
      }

      final String locationData = _gson.toJson(location);

      StreamEndpoint endpoint = null;
      try {
         endpoint = sendPostRequest(
               Sc7StreamsManagementServiceEndpoints.SC7_MANAGEMENT_SERVICE_REGISTER_STREAM_ENDPOINT,
               locationData, StreamEndpoint.class, null);
      } catch (IOException e) {
         if (errorListener != null) {
            errorListener.onErrorIntercepted("There is a problem with performing the create stream request!");
         }
         e.printStackTrace();
      }

      return endpoint;
   }

   @Override
   public StreamEndpoint requestNextStreamEndpoint(StreamInfo streamInfo,
         StreamingRequestErrorListener errorListener) {
      final String streamInfoData = _gson.toJson(streamInfo);

      StreamEndpoint endpoint = null;
      try {
         endpoint = sendPostRequest(
               Sc7StreamsManagementServiceEndpoints.SC7_MANAGEMENT_SERVICE_NEXT_STREAM_ENDPOINT,
               streamInfoData, StreamEndpoint.class, errorListener);
      } catch (IOException e) {
         if (errorListener != null) {
            errorListener.onErrorIntercepted("There is a problem with requesting next stream information!");
         }

         e.printStackTrace();
      }

      return endpoint;
   }

   public StreamReport requestUpdateOwnStreamInfo(StreamInfo streamInfo,
         StreamingRequestErrorListener errorListener) {
      final String streamInfoData = _gson.toJson(streamInfo);
      StreamReport result = null;
      try {
         result = sendPostRequest(
               Sc7StreamsManagementServiceEndpoints.SC7_MANAGEMENT_SERVICE_UPDATE_STREAM_INFO_ENDPOINT,
               streamInfoData, StreamReport.class, null);
      } catch (IOException e) {
         if (errorListener != null) {
            errorListener.onErrorIntercepted("There is a problem with updating stream information!");
         }
         e.printStackTrace();
      }

      return result;
   }

   private JSONObject transformInputStreamIntoJson(InputStream input)
         throws IOException, JSONException {
      BufferedReader streamReader = new BufferedReader(
            new InputStreamReader(input, "UTF-8"));
      StringBuilder responseStrBuilder = new StringBuilder();

      String inputStr;
      while ((inputStr = streamReader.readLine()) != null) {
         responseStrBuilder.append(inputStr);
      }

      JSONObject jsonObject = new JSONObject(responseStrBuilder.toString());
      return jsonObject;
   }

   private <T> T sendPostRequest(String url, String postData, Class<T> clazz,
         StreamingRequestErrorListener errorListener) throws IOException {
      URL u = new URL(url);
      HttpURLConnection urlConn = (HttpURLConnection) u.openConnection();
      urlConn.setReadTimeout(15000);
      urlConn.setConnectTimeout(15000);
      urlConn.setDoOutput(true);
      urlConn.setDoInput(true);
      urlConn.setUseCaches(false);
      urlConn.setAllowUserInteraction(false);
      urlConn.setRequestProperty("Content-Type", "application/json");
      urlConn.setRequestProperty("Accept", "*/*");
      urlConn.connect();
      OutputStream os = urlConn.getOutputStream();
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
      writer.write(postData);
      writer.flush();
      writer.close();

      int responseCode = urlConn.getResponseCode();
      InputStream inResponse = null;
      T result = null;

      inResponse = urlConn.getInputStream();
      InputStreamReader reader = new InputStreamReader(inResponse);
      result = _gson.fromJson(reader, clazz);

      return result;
   }
}
