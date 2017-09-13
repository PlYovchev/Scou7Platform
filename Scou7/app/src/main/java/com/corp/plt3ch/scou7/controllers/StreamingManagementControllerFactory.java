package com.corp.plt3ch.scou7.controllers;

import android.content.Context;

/**
 * Created by Plamen on 9/4/2017.
 */

public class StreamingManagementControllerFactory {

    public static StreamingManagementController getSteamingManagementController(Context context) {
        return StreamingManagementWebController.getInstance(context);
    }
}
