#include <string.h>

#include "query-using-rtsp-media-factory.h"

#define SCT_QUERY_USING_RTSP_MEDIA_FACTORY_GET_LOCK(f)       (&(GST_RTSP_MEDIA_FACTORY_CAST(f)->priv->lock))
#define SCT_QUERY_USING_RTSP_MEDIA_FACTORY_LOCK(f)           (g_mutex_lock(SCT_QUERY_USING_RTSP_MEDIA_FACTORY_GET_LOCK(f)))
#define SCT_QUERY_USING_RTSP_MEDIA_FACTORY_UNLOCK(f)         (g_mutex_unlock(SCT_QUERY_USING_RTSP_MEDIA_FACTORY_GET_LOCK(f)))

struct _GstRTSPMediaFactoryPrivate
{
	GMutex lock;                  /* protects everything but medias */
	GstRTSPPermissions *permissions;
	gchar *launch;
	gboolean shared;
	GstRTSPSuspendMode suspend_mode;
	gboolean eos_shutdown;
	GstRTSPProfile profiles;
	GstRTSPLowerTrans protocols;
	guint buffer_size;
	GstRTSPAddressPool *pool;
	GstRTSPTransportMode transport_mode;
	gboolean stop_on_disconnect;
	gchar *multicast_iface;

	GstClockTime rtx_time;
	guint latency;

	GMutex medias_lock;
	GHashTable *medias;           /* protected by medias_lock */

	GType media_gtype;

	GstClock *clock;

	GstRTSPPublishClockMode publish_clock_mode;
};

typedef struct
{
	gchar * key;
	gchar * value;
} QueryParam;

static GstElement* create_element_using_query_params(GstRTSPMediaFactory *factory, const GstRTSPUrl *url);
static GList* sct_get_query_params_from_query(const GstRTSPUrl *url);
static void sct_free_query_params_in_list(GList* queryParamsList);
static gchar* sct_get_formatted_launch_string(const gchar *launch, const GList *queryParamsList);

G_DEFINE_TYPE(SctQueryUsingRtspMediaFactory, sct_query_using_rtsp_media_factory, GST_TYPE_RTSP_MEDIA_FACTORY);

static void
sct_query_using_rtsp_media_factory_class_init(SctQueryUsingRtspMediaFactoryClass * klass)
{
	GstRTSPMediaFactoryClass *parentKlass = (GstRTSPMediaFactoryClass *)(klass);
	parentKlass->create_element = create_element_using_query_params;
}

static void
sct_query_using_rtsp_media_factory_init(SctQueryUsingRtspMediaFactory * media)
{
}

static GstElement * 
create_element_using_query_params(GstRTSPMediaFactory *factory, const GstRTSPUrl *url)
{
	GstRTSPMediaFactoryPrivate *priv = factory->priv;
	GstElement *element;
	GError *error = NULL;

	GList *queryParamsList = sct_get_query_params_from_query(url);
	gchar *formattedLaunchCommand = sct_get_formatted_launch_string(priv->launch, queryParamsList);

	sct_free_query_params_in_list(queryParamsList);
	g_list_free(queryParamsList);

	/* you can see at query string: */
	g_print("query is: %s\n", url->query);

	SCT_QUERY_USING_RTSP_MEDIA_FACTORY_LOCK(factory);
	/* we need a parse syntax */
	if (formattedLaunchCommand == NULL)
		goto no_launch;

	/* parse the user provided launch line */
	element =
		gst_parse_launch_full(formattedLaunchCommand, NULL, GST_PARSE_FLAG_PLACE_IN_BIN,
			&error);

	g_free(formattedLaunchCommand);
	if (element == NULL)
		goto parse_error;

	SCT_QUERY_USING_RTSP_MEDIA_FACTORY_UNLOCK(factory);

	if (error != NULL) {
		/* a recoverable error was encountered */
		GST_WARNING("recoverable parsing error: %s", error->message);
		g_error_free(error);
	}
	return element;

	/* ERRORS */
no_launch:
	{
		SCT_QUERY_USING_RTSP_MEDIA_FACTORY_UNLOCK(factory);
		g_critical("no launch line specified");
		return NULL;
	}
parse_error:
	{
		g_critical("could not parse launch syntax (%s): %s", priv->launch,
			(error ? error->message : "unknown reason"));
		SCT_QUERY_USING_RTSP_MEDIA_FACTORY_UNLOCK(factory);
		if (error)
			g_error_free(error);
		return NULL;
	}


	
	///* according to query create GstElement, for example: */
	//GstElement *element;
	//GError *error = NULL;

	//element = gst_parse_launch("( videotestsrc ! x264enc ! rtph264pay name=pay0 pt=96 )",
	//	&error);
	//return element;
}

static GList * 
sct_get_query_params_from_query(const GstRTSPUrl *url)
{
	GList* list = NULL;
	QueryParam* queryParam = NULL;
	gchar *paramsDelim, *paramPair, *keyValueDelim;
	gchar *remainderQuery;

	gchar *queryToProcess = g_strconcat(url->query, "&", NULL);
	remainderQuery = queryToProcess;

	while (paramsDelim = strchr(remainderQuery, '&'))
	{
		paramPair = g_strndup(remainderQuery, paramsDelim - remainderQuery);
		if (keyValueDelim = strchr(paramPair, '='))
		{
			queryParam = g_new0(QueryParam, 1);
			queryParam->key = g_strndup(paramPair, keyValueDelim - paramPair);
			g_print("key is: %s\n", queryParam->key);
			queryParam->value = g_strdup(keyValueDelim + 1);
			g_print("value is: %s\n", queryParam->value);
			list = g_list_append(list, queryParam);

			queryParam = NULL;
			keyValueDelim = NULL;
		}

		g_free(paramPair);
		paramPair = NULL;
		remainderQuery = paramsDelim + 1;
		paramsDelim = NULL;
	}

	g_free(queryToProcess);
	return list;
}

static gchar *
sct_get_key_in_token_format(gchar* key)
{
	return g_strdup_printf("%%%s%%", key);
}

static gchar *
sct_get_formatted_launch_string(const gchar *launch, const GList *queryParamsList)
{
	const GList *currentList;
	QueryParam *queryParam;
	gchar *position = NULL;
	gchar *keyInTokenFormat = NULL;
	gchar *firstPartOfLaunchCommand = NULL, *lastPartOfLaunchCommand = NULL;
	gchar *formattedLaunchCommand = g_strdup(launch);

	for (currentList = queryParamsList; currentList; currentList = currentList->next)
	{
		queryParam = (QueryParam*) currentList->data;
		keyInTokenFormat = sct_get_key_in_token_format(queryParam->key);
		if (position = strstr(formattedLaunchCommand, keyInTokenFormat))
		{
			firstPartOfLaunchCommand = g_strndup(formattedLaunchCommand, 
				position - formattedLaunchCommand);

			gsize keyTokenLenght = strlen(keyInTokenFormat);
			position = position + keyTokenLenght;
			lastPartOfLaunchCommand = g_strdup(position);

			g_free(formattedLaunchCommand);

			formattedLaunchCommand = g_strdup_printf("%s%s%s",
				firstPartOfLaunchCommand,
				queryParam->value,
				lastPartOfLaunchCommand);

			g_free(firstPartOfLaunchCommand);
			g_free(lastPartOfLaunchCommand);
		}

		g_free(keyInTokenFormat);
		keyInTokenFormat = NULL;
	}

	return formattedLaunchCommand;
}

static void
sct_free_query_params_in_list(GList* queryParamsList)
{
	GList *currentList;
	QueryParam *queryParam;

	for (currentList = queryParamsList; currentList; currentList = currentList->next)
	{
		queryParam = (QueryParam*)currentList->data;
		g_free(queryParam->key);
		g_free(queryParam->value);
		g_free(queryParam);
		currentList->data = NULL;
	}
}

SctQueryUsingRtspMediaFactory *
sct_query_using_rtsp_media_factory_new(void) 
{
	SctQueryUsingRtspMediaFactory *result;
	result = (SctQueryUsingRtspMediaFactory*) g_object_new (SCT_TYPE_QUERY_USING_RTSP_MEDIA_FACTORY, NULL);
	return result;
}