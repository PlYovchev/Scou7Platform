#include <gst/gst.h>

#include <gst/rtsp-server/rtsp-server.h>
#include "gstExtensions\rtsp-server\query-using-rtsp-media-factory.h"

int
main(int argc, char *argv[])
{
	GMainLoop *loop;
	GstRTSPServer *server;
	GstRTSPMountPoints *mounts;
	GstRTSPMediaFactory *factory;

	gst_init(&argc, &argv);

	loop = g_main_loop_new(NULL, FALSE);

	/* create a server instance */
	server = gst_rtsp_server_new();

	/* get the mount points for this server, every server has a default object
	* that be used to map uri mount points to media factories */
	mounts = gst_rtsp_server_get_mount_points(server);

	/* make a media factory for a test stream. The default media factory can use
	* gst-launch syntax to create pipelines.
	* any launch line works as long as it contains elements named pay%d. Each
	* element with pay%d names will be a stream */
	factory = (GstRTSPMediaFactory*) sct_query_using_rtsp_media_factory_new();
	gst_rtsp_media_factory_set_launch(factory,
		"( udpsrc port=%port% caps=\"application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)THEORA\" ! rtptheoradepay ! rtptheorapay name=pay0 pt=96 )");

	gst_rtsp_media_factory_set_shared(factory, TRUE);

	/* attach the test factory to the /test url */
	gst_rtsp_mount_points_add_factory(mounts, "/sc7rtsp", factory);

	/* don't need the ref to the mapper anymore */
	g_object_unref(mounts);

	/* attach the server to the default maincontext */
	gst_rtsp_server_attach(server, NULL);

	/* start serving */
	g_print("stream ready at rtsp://127.0.0.1:8554/sc7rtsp\n");
	g_main_loop_run(loop);

	return 0;
}