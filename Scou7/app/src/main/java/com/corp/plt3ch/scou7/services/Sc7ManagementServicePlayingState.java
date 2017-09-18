package com.corp.plt3ch.scou7.services;

import android.app.Service;

public class Sc7ManagementServicePlayingState
      extends Sc7ManagementServicePeriodicUpdatesState {

   public Sc7ManagementServicePlayingState(Service service) {
      super(service);
   }

   protected final void startSendingPeriodicUpdates() {
      _timer.scheduleAtFixedRate(new TimeDisplayTimerTask(), 1000, 1000);
   }
}
