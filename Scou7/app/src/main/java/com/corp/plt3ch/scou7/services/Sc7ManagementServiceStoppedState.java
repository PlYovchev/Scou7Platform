package com.corp.plt3ch.scou7.services;

import android.app.Service;

public class Sc7ManagementServiceStoppedState
      extends Sc7ManagementServicePeriodicUpdatesState {

   public Sc7ManagementServiceStoppedState(Service service) {
      super(service);
   }

   protected final void startSendingPeriodicUpdates() {
      _timer.scheduleAtFixedRate(new TimeDisplayTimerTask(), 1000, 5000);
   }
}
