package com.corp.plt3ch.scou7.services;

import android.os.Handler;
import android.os.HandlerThread;

import com.corp.plt3ch.scou7.controllers.Sc7SteamingManagementController;

import java.util.Timer;
import java.util.TimerTask;

public abstract class Sc7ManagementServicePeriodicUpdatesState
        implements Sc7ManagementServiceState {
    protected final Timer _timer;

    private final Handler _handler;
    private final HandlerThread _handlerThread;
    private final Sc7SteamingManagementController _controller;

    public Sc7ManagementServicePeriodicUpdatesState() {
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
                    _controller.requestUpdateOwnStreamInfo();
                }
            });
        }
    }
}
