package com.applitools.connectivity.api;

import com.applitools.eyes.AbstractProxySettings;

import java.net.URI;

public interface HttpClient {
    /**
     * Creates a new web resource target.
     * @param baseUrl The base url of the server.
     * @return The created target
     */
    Target target(URI baseUrl);

    /**
     * Creates a new web resource target.
     * @param path The base url of the server.
     * @return The created target
     */
    Target target(String path);

    AbstractProxySettings getProxySettings();

    int getTimeout();
}
