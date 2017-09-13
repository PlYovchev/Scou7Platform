package com.corp.plt3ch.scou7.services;

public class Sc7ManagementServiceStoppedState
        extends Sc7ManagementServicePeriodicUpdatesState {

    protected final void startSendingPeriodicUpdates() {
        _timer.scheduleAtFixedRate(new TimeDisplayTimerTask(), 1000, 5000);
    }
}
