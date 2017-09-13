package com.corp.plt3ch.scou7.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.corp.plt3ch.scou7.R;
import com.corp.plt3ch.scou7.Scou7StreamingState;
import com.corp.plt3ch.scou7.controllers.Sc7LocationManager;
import com.corp.plt3ch.scou7.controllers.Sc7SteamingManagementController;
import com.corp.plt3ch.scou7.models.StreamEndpoint;
import com.corp.plt3ch.scou7.models.StreamState;
import com.corp.plt3ch.scou7.services.Scou7ManagementService;
import com.corp.plt3ch.scou7.services.Scou7ManagementService.Scou7ManagementServiceBinder;

import org.freedesktop.gstreamer.GStreamer;

public class Scou7MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private native void nativeInit(String port);
    private native void nativeFinalize();
    private native void nativePlay();
    private native void nativePause();
    private native void nativeLoadNextStream(String port);
    private static native boolean classInit();
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private long native_custom_data;

    private static final String TAG = Scou7MainActivity.class.getSimpleName();

    private ImageButton _playButton;
    private FloatingActionButton _nextStreamButton;
    private ProgressBar _loadingVideoBar;

    private SurfaceView _videoArea;
    private SurfaceHolder _surfaceHolder;

    private boolean _playing;
    private Bundle initialization_data;

    private Sc7SteamingManagementController _sc7SteamingManagementController = null;

    private boolean _boundToScou7ManagementService = false;
    private Scou7ManagementService _scou7ManagementService = null;

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            Scou7ManagementService.Scou7ManagementServiceBinder binder = (Scou7ManagementServiceBinder) service;
            _scou7ManagementService = binder.getService();
            _boundToScou7ManagementService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            _boundToScou7ManagementService = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_scou7_main);

        _sc7SteamingManagementController = Sc7SteamingManagementController
                .getSc7SteamingManagementController(this.getApplicationContext());

        // Find the inner views and set their instances locally
        setViewsInstances();

        initPlayButton();
        initNextStreamButton();

        _surfaceHolder = _videoArea.getHolder();
        _surfaceHolder.addCallback(this);

        initialization_data = savedInstanceState;

        Intent intent = new Intent(this, Scou7ManagementService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        if (_sc7SteamingManagementController.getCurrentNextStreamEndpoint() != null) {
            new RequestNextStreamTask().execute();
        } else {
            new CreateNewStreamTask().execute();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        Log.d("GStreamer", "Saving state, playing:" + _playing);
        outState.putBoolean("playing", _playing);
    }

    protected void onDestroy() {
        nativeFinalize();
        super.onDestroy();
    }

    private void setViewsInstances() {
        _playButton = (ImageButton) findViewById(R.id.playButton);
        _nextStreamButton = (FloatingActionButton) findViewById(R.id.nextStreamButton);
        _loadingVideoBar = (ProgressBar) findViewById(R.id.loadingVideoBar);
        _videoArea = (SurfaceView) findViewById(R.id.videoArea);
    }

    private void initPlayButton() {
        _playButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                _playButton.setVisibility(View.GONE);
                _nextStreamButton.setVisibility(View.VISIBLE);
                nativePlay();
                changeStateToPlaying();
            }
        });
    }

    private void initNextStreamButton() {
        _nextStreamButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                _sc7SteamingManagementController.clearCurrentNextStreamEndpoint();
                new RequestNextStreamTask().execute();
            }
        });
    }

    private void changeManagementServiceState(Scou7StreamingState state) {
        if (!_boundToScou7ManagementService) {
            return;
        }

        _scou7ManagementService.setState(state);
    }

    private void changeStateToPlaying() {
        _playing = true;
        _sc7SteamingManagementController.updateOwnStreamState(StreamState.PLAYING);
        new UpdateOwnStreamStateTask().execute();
        changeManagementServiceState(Scou7StreamingState.STREAMING);
    }


    /* Called from native code */
    private void onGStreamerInitialized() {
        if (initialization_data != null) {
            boolean should_play = initialization_data.getBoolean("playing");
            Log.i("GStreamer", "Restoring state, playing:" + should_play);
            if (should_play) {
                nativePlay();
                changeStateToPlaying();
            } else {
                nativePause();
            }
        } else {
            nativePause();
        }
    }

    /* Called from native code */
    private void setCurrentState(int state) {
        Log.d("GStreamer", "State has changed to " + state);
        _playing = (state == 4);
    }

    /* Called from native code */
    private void setMessage(final String message) {
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("Scou7GstreamerController");
        classInit();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit(holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize();
    }

    private class CreateNewStreamTask extends AsyncTask<Void, Void, Boolean> {

        protected void onPreExecute() {
            if (_playButton != null && _loadingVideoBar != null) {
                _playButton.setVisibility(View.GONE);
                _loadingVideoBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (_sc7SteamingManagementController.getOwnStreamEndpoint() != null) {
                return true;
            }
            return _sc7SteamingManagementController.createOwnStreamEndpoint();
        }

        @Override
        protected void onPostExecute(Boolean streamEndpointCreated) {
            if (_playButton != null && _loadingVideoBar != null) {
                _playButton.setVisibility(View.VISIBLE);
                _loadingVideoBar.setVisibility(View.GONE);
            }
            if (streamEndpointCreated) {
                StreamEndpoint endpoint = _sc7SteamingManagementController.getOwnStreamEndpoint();
                changeManagementServiceState(Scou7StreamingState.STOPPED);
                nativeInit(String.valueOf(endpoint.getPort()));
                nativeSurfaceInit(_surfaceHolder.getSurface());
            }
        }
    }

    private class RequestNextStreamTask extends AsyncTask<Void, Void, Boolean> {

        protected void onPreExecute() {
            if (_loadingVideoBar != null) {
                _loadingVideoBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (_sc7SteamingManagementController.getCurrentNextStreamEndpoint() != null) {
                return true;
            }
            return _sc7SteamingManagementController.requestNextStreamEndpoint();
        }

        @Override
        protected void onPostExecute(Boolean streamEndpointCreated) {
            if (_loadingVideoBar != null) {
                _loadingVideoBar.setVisibility(View.GONE);
            }
            if (streamEndpointCreated) {
                StreamEndpoint endpoint = _sc7SteamingManagementController.getCurrentNextStreamEndpoint();
                nativeLoadNextStream(String.valueOf(endpoint.getPort()));
            } else {
                Toast.makeText(Scou7MainActivity.this, "There is no streams nearby!", Toast.LENGTH_LONG);
            }
        }
    }

    private class UpdateOwnStreamStateTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            return _sc7SteamingManagementController.requestUpdateOwnStreamInfo();
        }
    }
}
