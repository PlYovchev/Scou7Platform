#include <gst/rtsp-server/rtsp-media-factory.h>

#define SCT_TYPE_QUERY_USING_RTSP_MEDIA_FACTORY	(sct_query_using_rtsp_media_factory_get_type ())

typedef struct _SctQueryUsingRtspMediaFactory SctQueryUsingRtspMediaFactory;
typedef struct _SctQueryUsingRtspMediaFactoryClass SctQueryUsingRtspMediaFactoryClass;

struct _SctQueryUsingRtspMediaFactory
{
	GstRTSPMediaFactory parent;

	gpointer _gst_reserved[GST_PADDING];
};

struct _SctQueryUsingRtspMediaFactoryClass
{
	GstRTSPMediaFactoryClass parent;

	/*< private >*/
	gpointer         _gst_reserved[GST_PADDING_LARGE];
};

GType 
sct_query_using_rtsp_media_factory_get_type(void);

SctQueryUsingRtspMediaFactory*
sct_query_using_rtsp_media_factory_new(void);