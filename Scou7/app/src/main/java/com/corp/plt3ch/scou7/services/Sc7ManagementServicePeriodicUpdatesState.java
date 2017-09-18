package com.corp.plt3ch.scou7.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.corp.plt3ch.scou7.StreamingInternalBroadcastConstants;
import com.corp.plt3ch.scou7.controllers.Sc7SteamingManagementController;
import com.corp.plt3ch.scou7.models.StreamReport;

import java.util.Timer;
import java.util.TimerTask;

public abstract class Sc7ManagementServicePeriodicUpdatesState
      implements Sc7ManagementServiceState {

   private static final String TAG = Sc7ManagementServicePeriodicUpdatesState.class.getSimpleName();

   protected final Timer _timer;

   private final Handler _handler;
   private final HandlerThread _handlerThread;
   private final Sc7SteamingManagementController _controller;

   private final Service _sc7Service;

   public Sc7ManagementServicePeriodicUpdatesState(Service service) {
      _sc7Service = service;
      _timer = new Timer();
      _handlerThread = new HandlerThread("ThreadOfStreamUpdates");
      _handlerThread.start();
      _handler = new Handler(_handlerThread.getLooper());
      _controller = Sc7SteamingManagementController
            .getSc7SteamingManagementController(null);
   }

   @Override
   public void start() {
      startSendingPeriodicUpdates();
   }

   @Override
   public void stop() {
      stopPeriodicUpdates();
      _handlerThread.quit();
   }

   protected abstract void startSendingPeriodicUpdates();

   private void stopPeriodicUpdates() {
      _timer.cancel();
   }

   protected class TimeDisplayTimerTask extends TimerTask {

      @Override
      public void run() {
         // run on another thread
         _handler.post(new Runnable() {
            @Override
            public void run() {
               _controller.updateOwnStreamInfoLocation();
               StreamReport report = _controller.requestUpdateOwnStreamInfo(null);
               if (report != null && !report.isWatchingStreamOn()
                     && _controller.getCurrentNextStreamEndpoint() != null) {
                  Intent intent = new Intent(StreamingInternalBroadcastConstants.STREAMING_BROADCAST_INTENT_FILTER)
                        .putExtra(StreamingInternalBroadcastConstants.STREAMING_BROADCAST_MSG,
                              StreamingInternalBroadcastConstants.STREAMING_BROADCAST_WATCHING_STREAM_IS_OFF);
                  _sc7Service.sendBroadcast(intent);
                  // send broadcast to stop watching
                  Log.d(TAG, "stop watching current stream. It is stopped.");
               }
            }
         });
      }
   }
}
