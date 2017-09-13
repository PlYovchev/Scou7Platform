package com.corp.plt3ch.scou7.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.corp.plt3ch.scou7.Scou7StreamingState;

public class Scou7ManagementService extends Service {

    private Sc7ManagementServiceState _sc7ServiceState;
    private Scou7StreamingState _state;

    private final IBinder mBinder = new Scou7ManagementServiceBinder();

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void setState(Scou7StreamingState streamingState) {
        if (_state == streamingState) {
            return;
        }

        if (_sc7ServiceState != null) {
            _sc7ServiceState.stop();
        }

        switch (streamingState) {
            case STOPPED:
                _sc7ServiceState = new Sc7ManagementServiceStoppedState();
                break;
            case STREAMING:
                _sc7ServiceState = new Sc7ManagementServicePlayingState();
                break;
        }

        if (_sc7ServiceState != null) {
            _sc7ServiceState.start();
        }

        _state = streamingState;
    }

    public class Scou7ManagementServiceBinder extends Binder {
        public Scou7ManagementService getService() {
            return Scou7ManagementService.this;
        }
    }
}
