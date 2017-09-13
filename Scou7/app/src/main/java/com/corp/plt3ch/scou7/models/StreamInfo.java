package com.corp.plt3ch.scou7.models;

public class StreamInfo {
    private String uniqueId;
    private GeoLocation location;
    private StreamState streamState;

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public GeoLocation getLocation() {
        return location;
    }

    public void setLocation(GeoLocation location) {
        this.location = location;
    }

    public StreamState getStreamState() {
        return streamState;
    }

    public void setStreamState(StreamState streamState) {
        this.streamState = streamState;
    }
}
