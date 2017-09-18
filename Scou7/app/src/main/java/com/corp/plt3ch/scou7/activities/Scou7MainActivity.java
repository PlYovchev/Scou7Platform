package com.corp.plt3ch.scou7.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.corp.plt3ch.scou7.R;
import com.corp.plt3ch.scou7.Scou7StreamingState;
import com.corp.plt3ch.scou7.StreamingInternalBroadcastConstants;
import com.corp.plt3ch.scou7.controllers.Sc7SteamingManagementController;
import com.corp.plt3ch.scou7.listeners.StreamingRequestErrorListener;
import com.corp.plt3ch.scou7.models.StreamEndpoint;
import com.corp.plt3ch.scou7.models.StreamReport;
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
   private native void nativeStopNextStream();
   private static native boolean classInit();
   private native void nativeSurfaceInit(Object surface);
   private native void nativeSurfaceFinalize();
   private long native_custom_data;

   private static final String TAG = Scou7MainActivity.class.getSimpleName();

   private ImageButton _playButton;
   private FloatingActionButton _nextStreamButton;
   private FloatingActionButton _stopStreamButton;
   private ProgressBar _loadingVideoBar;
   private TextView _streamingIndicator;
   private ViewGroup _liveVideoIndicator;

   private SurfaceView _videoArea;
   private SurfaceHolder _surfaceHolder;

   private boolean _playing;
   private boolean _watching;
   private Bundle initialization_data;

   private Sc7SteamingManagementController _sc7SteamingManagementController = null;
   private StreamingRequestErrorListener _errorListener = null;
   private BroadcastReceiver _sc7InternalBroadcastReceiver = null;

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
      _errorListener = new StreamingRequestErrorListenerImpl();

      // Find the inner views and set their instances locally
      setViewsInstances();

      initPlayButton();
      initNextStreamButton();
      initStopButton();

      _surfaceHolder = _videoArea.getHolder();
      _surfaceHolder.addCallback(this);

      initialization_data = savedInstanceState;

      initSc7BroadcastReceiver();

      Intent intent = new Intent(this, Scou7ManagementService.class);
      bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

      if (!isNetworkConnected()) {
         showWarningDialogWithMessage("There is no internet connection." +
               "\n\nActivate it and restart the app!", false);
         return;
      }

      if (!isLocationAvaiable()) {
         showWarningDialogWithMessage("There is no location service available." +
               "\n\nActivate your GPS and restart the app!", false);
         return;
      }

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
      _stopStreamButton = (FloatingActionButton) findViewById(R.id.stopStreamButton);
      _loadingVideoBar = (ProgressBar) findViewById(R.id.loadingVideoBar);
      _videoArea = (SurfaceView) findViewById(R.id.videoArea);
      _streamingIndicator = (TextView) findViewById(R.id.streamingIndicator);
      _liveVideoIndicator = (ViewGroup) findViewById(R.id.liveVideoIndicator);
   }

   private void initSc7BroadcastReceiver() {
      _sc7InternalBroadcastReceiver = new Sc7StreamingBroadcastReceiver();

      IntentFilter intentFilter = new IntentFilter(
            StreamingInternalBroadcastConstants.STREAMING_BROADCAST_INTENT_FILTER);
      this.registerReceiver(_sc7InternalBroadcastReceiver, intentFilter);
   }


   private void initPlayButton() {
      _playButton.setOnClickListener(new View.OnClickListener() {

         @Override
         public void onClick(View v) {
            enterPlayingState();
         }
      });
   }

   private void initNextStreamButton() {
      _nextStreamButton.setOnClickListener(new View.OnClickListener() {

         @Override
         public void onClick(View v) {
            _watching = true;
            _sc7SteamingManagementController.clearCurrentNextStreamEndpoint();
            new RequestNextStreamTask().execute();
         }
      });
   }

   private void initStopButton() {
      _stopStreamButton.setOnClickListener(new View.OnClickListener() {

         @Override
         public void onClick(View v) {
            if (_watching) {
               nativeStopNextStream();
               enterPlayingState();
            } else if (_playing) {
               enterPausedState();
            }
         }
      });
   }


   private void changeManagementServiceState(Scou7StreamingState state) {
      if (!_boundToScou7ManagementService) {
         return;
      }

      _scou7ManagementService.setState(state);
   }

   private void enterPlayingState() {
      configurePlayingStateViews();
      nativePlay();
      _playing = true;
      _watching = false;
      _sc7SteamingManagementController.clearCurrentNextStreamEndpoint();
      _sc7SteamingManagementController.updateOwnStreamState(StreamState.PLAYING);
      new UpdateOwnStreamStateTask().execute();
      changeManagementServiceState(Scou7StreamingState.STREAMING);
   }

   private void enterPausedState() {
      configurePausedStateViews();
      nativePause();
      _playing = false;
      _watching = false;
      _sc7SteamingManagementController.updateOwnStreamState(StreamState.STOPPED);
      new UpdateOwnStreamStateTask().execute();
      changeManagementServiceState(Scou7StreamingState.STOPPED);
   }

   private void configurePlayingStateViews() {
      _playButton.setVisibility(View.GONE);
      _liveVideoIndicator.setVisibility(View.GONE);
      _nextStreamButton.setVisibility(View.VISIBLE);
      _stopStreamButton.setVisibility(View.VISIBLE);
      _streamingIndicator.setVisibility(View.VISIBLE);
   }

   private void configurePausedStateViews() {
      _playButton.setVisibility(View.VISIBLE);
      _nextStreamButton.setVisibility(View.GONE);
      _stopStreamButton.setVisibility(View.GONE);
      _streamingIndicator.setVisibility(View.GONE);
      _liveVideoIndicator.setVisibility(View.GONE);
   }

   private void enterWatchingStreamState() {
      configureWatchingStreamStateViews();
   }

   private void configureWatchingStreamStateViews() {
      configurePlayingStateViews();
      _liveVideoIndicator.setVisibility(View.VISIBLE);
   }

   private boolean isNetworkConnected() {
      ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
      return cm.getActiveNetworkInfo() != null;
   }

   private boolean isLocationAvaiable() {
      LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
   }

   private void showWarningDialogWithMessage(String message, boolean cancelable) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(message);
      builder.setTitle("Warning!");
      builder.setCancelable(cancelable);
      if (cancelable) {
         builder.setPositiveButton("Ok", null);
      }
      AlertDialog dialog = builder.create();
      dialog.show();
   }

   /* Called from native code */
   private void onGStreamerInitialized() {
      if (initialization_data != null) {
         boolean should_play = initialization_data.getBoolean("playing");
         Log.i("GStreamer", "Restoring state, playing:" + should_play);
         if (should_play) {
            enterPlayingState();
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
         return _sc7SteamingManagementController.createOwnStreamEndpoint(_errorListener);
      }

      @Override
      protected void onPostExecute(Boolean streamEndpointCreated) {
         if (_playButton != null && _loadingVideoBar != null) {
            _loadingVideoBar.setVisibility(View.GONE);
         }
         if (streamEndpointCreated) {
            StreamEndpoint endpoint = _sc7SteamingManagementController.getOwnStreamEndpoint();
            changeManagementServiceState(Scou7StreamingState.STOPPED);
            nativeInit(String.valueOf(endpoint.getPort()));
            nativeSurfaceInit(_surfaceHolder.getSurface());
            _playButton.setVisibility(View.VISIBLE);
         } else {
            Toast.makeText(Scou7MainActivity.this, "The stream creation failed!", Toast.LENGTH_LONG)
                  .show();
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
         return _sc7SteamingManagementController.requestNextStreamEndpoint(_errorListener);
      }

      @Override
      protected void onPostExecute(Boolean streamEndpointCreated) {
         if (_loadingVideoBar != null) {
            _loadingVideoBar.setVisibility(View.GONE);
         }
         if (streamEndpointCreated) {
            StreamEndpoint endpoint = _sc7SteamingManagementController.getCurrentNextStreamEndpoint();
            nativeLoadNextStream(String.valueOf(endpoint.getPort()));
            enterWatchingStreamState();
         } else {
            Toast.makeText(Scou7MainActivity.this, "There are no streams nearby!", Toast.LENGTH_LONG)
                  .show();
         }
      }
   }

   private class UpdateOwnStreamStateTask extends AsyncTask<Void, Void, StreamReport> {
      @Override
      protected StreamReport doInBackground(Void... params) {
         return _sc7SteamingManagementController.requestUpdateOwnStreamInfo(_errorListener);
      }
   }

   private class StreamingRequestErrorListenerImpl implements StreamingRequestErrorListener {

      @Override
      public void onErrorIntercepted(final String errorMessage) {
         Scou7MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
               showWarningDialogWithMessage(errorMessage, true);
            }
         });
      }
   }

   private class Sc7StreamingBroadcastReceiver extends BroadcastReceiver {

      @Override
      public void onReceive(Context context, Intent intent) {
         String streamingMsg = intent.getStringExtra(
               StreamingInternalBroadcastConstants.STREAMING_BROADCAST_MSG);
         if (StreamingInternalBroadcastConstants.STREAMING_BROADCAST_WATCHING_STREAM_IS_OFF
               .equals(streamingMsg) && _watching) {
            nativeStopNextStream();
            enterPlayingState();
         }
      }
   }
}
