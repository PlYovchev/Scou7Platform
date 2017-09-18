#include <string.h>
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <pthread.h>
#include <gst/video/videooverlay.h>
#include <android/log.h>

#define APPNAME "SCOU7_GSTREAMER_CONTROLLER"

#define LOG_VERBOSE(msg) __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, msg, 1);

GST_DEBUG_CATEGORY_STATIC (debug_category);
#define GST_CAT_DEFAULT debug_category

/*
 * These macros provide a way to store the native pointer to CustomData, which might be 32 or 64 bits, into
 * a jlong, which is always 64 bits, without warnings.
 */
#if GLIB_SIZEOF_VOID_P == 8
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(*env)->GetLongField (env, thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) (*env)->SetLongField (env, thiz, fieldID, (jlong)data)
#else
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(jint)(*env)->GetLongField (env, thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) (*env)->SetLongField (env, thiz, fieldID, (jlong)(jint)data)
#endif

typedef struct _CustomData {
    jobject app;
    GstElement *pipeline;
    GstElement *videoReceivingPipeline;
    GMainLoop *main_loop;
    ANativeWindow *native_window;
    gboolean state;
    GstElement *vsink;
    GstElement *t;
    GstElement *queueToSink;
    GMainContext *context;
    gboolean initialized;
    gchar *streamingToPort;
} CustomData;

static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID set_current_state_method_id;
static jmethodID on_gstreamer_initialized_method_id;

static void set_ui_message (const gchar *message, CustomData *data);

/*
 * Private methods
 */
static JNIEnv *attach_current_thread (void) {
    JNIEnv *env;
    JavaVMAttachArgs args;

    GST_DEBUG ("Attaching thread %p", g_thread_self ());
    args.version = JNI_VERSION_1_4;
    args.name = NULL;
    args.group = NULL;

    if ((*java_vm)->AttachCurrentThread (java_vm, &env, &args) < 0) {
        GST_ERROR ("Failed to attach current thread");
        return NULL;
    }

    return env;
}

static void detach_current_thread (void *env) {
    GST_DEBUG ("Detaching thread %p", g_thread_self ());
    (*java_vm)->DetachCurrentThread (java_vm);
}

static JNIEnv *get_jni_env (void) {
    JNIEnv *env;

    if ((env = pthread_getspecific (current_jni_env)) == NULL) {
        env = attach_current_thread ();
        pthread_setspecific (current_jni_env, env);
    }

    return env;
}

static void set_ui_message (const gchar *message, CustomData *data) {
    JNIEnv *env = get_jni_env ();
    GST_DEBUG ("Setting message to: %s", message);
    jstring jmessage = (*env)->NewStringUTF(env, message);
    (*env)->CallVoidMethod (env, data->app, set_message_method_id, jmessage);
    if ((*env)->ExceptionCheck (env)) {
        GST_ERROR ("Failed to call Java method");
        (*env)->ExceptionClear (env);
    }
    (*env)->DeleteLocalRef (env, jmessage);
}

static void error_cb (GstBus *bus, GstMessage *msg, CustomData *data) {
    GError *err;
    gchar *debug_info;
    gchar *message_string;

    gst_message_parse_error (msg, &err, &debug_info);
    message_string = g_strdup_printf ("Error received from element %s: %s", GST_OBJECT_NAME (msg->src), err->message);
    g_clear_error (&err);
    set_ui_message (debug_info, data);
    g_free (debug_info);
    g_free (message_string);
    gst_element_set_state (data->pipeline, GST_STATE_NULL);
}

static void eos_cb (GstBus *bus, GstMessage *msg, CustomData *data) {
    set_ui_message (GST_MESSAGE_TYPE_NAME (msg), data);
    gst_element_set_state (data->pipeline, GST_STATE_PAUSED);
}

static void state_changed_cb (GstBus *bus, GstMessage *msg, CustomData *data) {
    JNIEnv *env = get_jni_env ();
    GstState old_state, new_state, pending_state;

    gst_message_parse_state_changed (msg, &old_state, &new_state, &pending_state);
    /* Only pay attention to messages coming from the pipeline, not its children */
    if (GST_MESSAGE_SRC (msg) == GST_OBJECT (data->pipeline)) {
        data->state = new_state;
        GST_DEBUG ("State changed to %s, notifying application", gst_element_state_get_name(new_state));
        (*env)->CallVoidMethod (env, data->app, set_current_state_method_id, new_state);
        if ((*env)->ExceptionCheck (env)) {
            GST_ERROR ("Failed to call Java method");
            (*env)->ExceptionClear (env);
        }
    }
}

static void check_initialization_complete (CustomData *data) {
    JNIEnv *env = get_jni_env ();
    LOG_VERBOSE("Trying to complete  the initialization.");
    /* Check if all conditions are met to report GStreamer as initialized.
     * These conditions will change depending on the application */
    if (!data->initialized && data->native_window && data->main_loop) {
        LOG_VERBOSE("Initialization complete");
        data->initialized = TRUE;
        (*env)->CallVoidMethod (env, data->app, on_gstreamer_initialized_method_id);
        if ((*env)->ExceptionCheck (env)) {
            GST_ERROR ("Failed to call Java method");
            (*env)->ExceptionClear (env);
        }
    }
}

static void *app_function (void *userdata) {
    JavaVMAttachArgs args;
    GstBus *bus;
    GstMessage *msg;
    CustomData *data = (CustomData *)userdata;
    GSource *bus_source;

    GST_DEBUG ("Creating pipeline in CustomData at %p", data);
    LOG_VERBOSE("Creating pipeline in CustomData");

    /* create our own GLib Main Context, so we do not interfere with other libraries using GLib */
    data->context = g_main_context_new ();

    gchar *pipelineCmd = g_strdup_printf ("rtpbin name=rtpbin ahcsrc ! tee name=t ! queue ! videoconvert ! theoraenc "
          "! video/x-theora, width=320, height=240 "
          "! rtptheorapay name=theorapay config-interval=1 ! rtpbin.send_rtp_sink_0 rtpbin.send_rtp_src_0 "
          "! udpsink port=%s host=192.168.1.104 t. ! queue name=queueToSink ! identity drop-probability=0 name=identity  "
          "! glimagesink name=vsink sync=false", data->streamingToPort);

    LOG_VERBOSE(pipelineCmd);

    data->pipeline = gst_parse_launch (pipelineCmd, NULL);

    g_free(pipelineCmd);

    if (!data->pipeline) {
        LOG_VERBOSE("The pipeline is null.");
        return NULL;
    }

    data->vsink = gst_bin_get_by_name (GST_BIN (data->pipeline), "vsink");
    if (!data->vsink) {
        LOG_VERBOSE ("vsink is null.");
        data->vsink = gst_object_ref (data->pipeline);
    }

    data->t = gst_bin_get_by_name (GST_BIN (data->pipeline), "t");
    data->queueToSink = gst_bin_get_by_name (GST_BIN (data->pipeline), "queueToSink");

    if (data->native_window) {
        LOG_VERBOSE ("Native window already received, notifying the vsink about it.");
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->vsink), (guintptr)data->native_window);
    }

    LOG_VERBOSE("The pipeline is not null.");

    /* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
    bus = gst_element_get_bus (data->pipeline);
    bus_source = gst_bus_create_watch (bus);
    g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func, NULL, NULL);
    g_source_attach (bus_source, data->context);
    g_source_unref (bus_source);
    g_signal_connect (G_OBJECT (bus), "message::error", (GCallback)error_cb, data);
    g_signal_connect (G_OBJECT (bus), "message::eos", (GCallback)eos_cb, data);
    g_signal_connect (G_OBJECT (bus), "message::state-changed", (GCallback)state_changed_cb, data);
    gst_object_unref (bus);

    /* Create a GLib Main Loop and set it to run */
    GST_DEBUG ("Entering main loop... (CustomData:%p)", data);
    LOG_VERBOSE("Entering main loop...");
    data->main_loop = g_main_loop_new (data->context, FALSE);
    check_initialization_complete (data);
    g_main_loop_run (data->main_loop);
    LOG_VERBOSE("Exited main loop");
    GST_DEBUG ("Exited main loop");
    g_main_loop_unref (data->main_loop);
    data->main_loop = NULL;

    /* Free resources */
    g_main_context_unref (data->context);
    gst_element_set_state (data->pipeline, GST_STATE_NULL);
    gst_object_unref (data->pipeline);

    return NULL;
}

/*
 * Java Bindings
 */
void gst_native_init (JNIEnv* env, jobject thiz, jstring port) {
    CustomData *data = (CustomData *)g_malloc0 (sizeof (CustomData));

    SET_CUSTOM_DATA (env, thiz, custom_data_field_id, data);
    GST_DEBUG ("Created CustomData at %p", data);

    const gchar *nativeString = (*env)->GetStringUTFChars(env, port, 0);
    if (data->streamingToPort) {
        g_free(data->streamingToPort);
        data->streamingToPort = NULL;
    }
    data->streamingToPort = g_strdup (nativeString);
    (*env)->ReleaseStringUTFChars(env, port, nativeString);

    data->app = (*env)->NewGlobalRef (env, thiz);
    GST_DEBUG ("Created GlobalRef for app object at %p", data->app);
    pthread_create (&gst_app_thread, NULL, &app_function, data);
}

void gst_native_finalize (JNIEnv* env, jobject thiz) {
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);

    if (!data) return;
    GST_DEBUG ("Quitting main loop...");
    g_main_loop_quit (data->main_loop);
    GST_DEBUG ("Waiting for thread to finish...");
    pthread_join (gst_app_thread, NULL);
    GST_DEBUG ("Deleting GlobalRef at %p", data->app);
    (*env)->DeleteGlobalRef (env, data->app);
    GST_DEBUG ("Freeing CustomData at %p", data);
    g_free (data);
    SET_CUSTOM_DATA (env, thiz, custom_data_field_id, NULL);
    GST_DEBUG ("Done finalizing");
}

void gst_native_play (JNIEnv* env, jobject thiz) {
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);

    if (!data) return;
    GST_DEBUG ("Setting state to PLAYING");
    gst_element_set_state (data->pipeline, GST_STATE_PLAYING);
}

void gst_native_pause (JNIEnv* env, jobject thiz) {
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);

    if (!data) return;
    GST_DEBUG ("Setting state to PAUSED");
    gst_element_set_state (data->pipeline, GST_STATE_PAUSED);
}

void gst_native_load_next_stream (JNIEnv* env, jobject thiz, jstring port) {
    GstBus *bus;
    GSource *bus_source;
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
    LOG_VERBOSE("Clearing old video receiving pipeline");
    if (!data) return;

    if (data->t && data->vsink) {
        LOG_VERBOSE("Clearing old vsink");
        gst_element_unlink(data->t, data->queueToSink);
        gst_element_set_state (data->vsink, GST_STATE_NULL);
        gst_object_unref (data->vsink);
        data->vsink = NULL;
//        g_object_set(data->identity, "drop-probability", 1.0f, NULL);
//        gst_pad_send_event(data->vsink, gst_event_new_eos());
//        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->vsink), 0);
//        gst_object_unref (data->vsink);
//
    }

    if (data->videoReceivingPipeline) {
        LOG_VERBOSE("Cleared the old video receiving pipeline");
        gst_element_set_state (data->videoReceivingPipeline, GST_STATE_NULL);
        gst_object_unref (data->videoReceivingPipeline);
    }

    const gchar *nativeString = (*env)->GetStringUTFChars(env, port, 0);

    gchar *pipelineCmd = g_strdup_printf ("rtspsrc location=rtsp://192.168.1.104:8554/sc7rtsp?port=%s ! rtptheoradepay "
                                                  "! theoradec ! glimagesink name=vsink sync=false", nativeString);

    LOG_VERBOSE(pipelineCmd);

    data->videoReceivingPipeline = gst_parse_launch (pipelineCmd, NULL);

    g_free(pipelineCmd);

    (*env)->ReleaseStringUTFChars(env, port, nativeString);

    if (!data->videoReceivingPipeline) {
        LOG_VERBOSE("No video receiving pipeline has been created");
        return;
    }

    LOG_VERBOSE("Video receiving pipeline has been created");

    data->vsink = gst_bin_get_by_name (GST_BIN (data->videoReceivingPipeline), "vsink");
    if (!data->vsink)
        data->vsink = gst_object_ref (data->videoReceivingPipeline);

    if (data->native_window) {
        LOG_VERBOSE ("Native window already received, notifying the vsink about it.");
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->vsink), (guintptr)data->native_window);
    }

    LOG_VERBOSE("Video receiving pipeline - setting bus");

    /* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
    bus = gst_element_get_bus (data->videoReceivingPipeline);
    bus_source = gst_bus_create_watch (bus);
    g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func, NULL, NULL);
    g_source_attach (bus_source, data->context);
    g_source_unref (bus_source);
    g_signal_connect (G_OBJECT (bus), "message::error", (GCallback)error_cb, data);
    g_signal_connect (G_OBJECT (bus), "message::eos", (GCallback)eos_cb, data);
    g_signal_connect (G_OBJECT (bus), "message::state-changed", (GCallback)state_changed_cb, data);
    gst_object_unref (bus);

    LOG_VERBOSE("Video receiving pipeline - setting playing state");
    gst_element_set_state (data->videoReceivingPipeline, GST_STATE_PLAYING);
}

void gst_native_stop_next_stream (JNIEnv* env, jobject thiz) {
    GstPad *tee_pad, *queue_pad;
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);

    if (!data) return;

    LOG_VERBOSE("Clearing old video receiving pipeline");
    if (data->videoReceivingPipeline) {
        LOG_VERBOSE("Cleared the old video receiving pipeline");
        gst_element_set_state (data->videoReceivingPipeline, GST_STATE_NULL);
        gst_object_unref (data->videoReceivingPipeline);
        data->videoReceivingPipeline = NULL;
    }

    if (data->vsink) {
        gst_object_unref (data->vsink);
        data->vsink = NULL;
    }

    if (data->t && data->queueToSink) {
        LOG_VERBOSE("Linking playing pipeline queue video sink.");
        tee_pad = gst_element_get_request_pad (data->t, "src_%u");
        queue_pad = gst_element_get_static_pad (data->queueToSink, "sink");
        gst_pad_link (tee_pad, queue_pad);
        gst_object_unref (tee_pad);
        gst_object_unref (queue_pad);
    }

    LOG_VERBOSE("Video receiving pipeline has been created");
    data->vsink = gst_bin_get_by_name (GST_BIN (data->pipeline), "vsink");
    if (!data->vsink)
        data->vsink = gst_object_ref (data->pipeline);

    gst_element_set_state (data->vsink, GST_STATE_PLAYING);
    if (data->native_window) {
        LOG_VERBOSE ("Native window already received, notifying the vsink about it.");
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->vsink), (guintptr)data->native_window);
    }
}

jboolean gst_class_init (JNIEnv* env, jclass klass) {
    custom_data_field_id = (*env)->GetFieldID (env, klass, "native_custom_data", "J");
    GST_DEBUG ("The FieldID for the native_custom_data field is %p", custom_data_field_id);
    set_message_method_id = (*env)->GetMethodID (env, klass, "setMessage", "(Ljava/lang/String;)V");
    GST_DEBUG ("The MethodID for the setMessage method is %p", set_message_method_id);
    on_gstreamer_initialized_method_id = (*env)->GetMethodID (env, klass, "onGStreamerInitialized", "()V");
    GST_DEBUG ("The MethodID for the onGStreamerInitialized method is %p", on_gstreamer_initialized_method_id);
    set_current_state_method_id = (*env)->GetMethodID (env, klass, "setCurrentState", "(I)V");
    GST_DEBUG ("The MethodID for the setCurrentState method is %p", set_current_state_method_id);

    if (!custom_data_field_id || !set_message_method_id ||
        !on_gstreamer_initialized_method_id || !set_current_state_method_id) {
        GST_ERROR ("The calling class does not implement all necessary interface methods");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

void gst_native_surface_init (JNIEnv *env, jobject thiz, jobject surface) {
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
    LOG_VERBOSE("Initializing the native surface");

    if (!data) return;

    LOG_VERBOSE ("Received surface");
    GST_DEBUG ("Received surface %p", surface);
    if (data->native_window) {
        GST_DEBUG ("Releasing previous native window %p", data->native_window);
        ANativeWindow_release (data->native_window);
    }
    data->native_window = ANativeWindow_fromSurface(env, surface);
    GST_DEBUG ("Got Native Window %p", data->native_window);

    if (data->vsink) {
        LOG_VERBOSE ("Pipeline already created, notifying the vsink about the native window.");
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->vsink), (guintptr)data->native_window);
    } else {
        LOG_VERBOSE ("Pipeline not created yet, vsink will later be notified about the native window.");
    }

    check_initialization_complete (data);
}

void gst_native_surface_finalize (JNIEnv *env, jobject thiz) {
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);

    if (!data) {
        GST_WARNING ("Received surface finalize but there is no CustomData. Ignoring.");
        return;
    }
    GST_DEBUG ("Releasing Native Window %p", data->native_window);
    ANativeWindow_release (data->native_window);
    data->native_window = NULL;

    gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->vsink), (guintptr)NULL);
}

static JNINativeMethod native_methods[] = {
        { "nativeInit", "(Ljava/lang/String;)V", (void *) gst_native_init },
        { "nativeFinalize", "()V", (void *) gst_native_finalize },
        { "nativePlay", "()V", (void *) gst_native_play },
        { "nativePause", "()V", (void *) gst_native_pause },
        { "nativeLoadNextStream" , "(Ljava/lang/String;)V", (void *) gst_native_load_next_stream },
        { "nativeStopNextStream", "()V", (void *) gst_native_stop_next_stream },
        { "classInit", "()Z", (void *) gst_class_init },
        { "nativeSurfaceInit", "(Ljava/lang/Object;)V", (void *) gst_native_surface_init },
        { "nativeSurfaceFinalize", "()V", (void *) gst_native_surface_finalize }
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;

    LOG_VERBOSE("Android Gstreamer Camera");

    setenv ("GST_DEBUG", "*:3,glcontext:7,gldisplay:7,glimagesink:5,ahcsrc:5", 1);
    setenv ("GST_DEBUG_NO_COLOR", "1", 1);
    setenv ("GST_DEBUG_FILE", "/data/local/tmp/gst.log", 1);

    GST_DEBUG_CATEGORY_INIT (debug_category, "camera-test", 0, "Android Gstreamer Camera test");

    java_vm = vm;

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        GST_ERROR ("Could not retrieve JNIEnv");
        return 0;
    }
    jclass klass = (*env)->FindClass (env, "com/corp/plt3ch/scou7/activities/Scou7MainActivity");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS(native_methods));

    pthread_key_create (&current_jni_env, detach_current_thread);

    return JNI_VERSION_1_4;
}
