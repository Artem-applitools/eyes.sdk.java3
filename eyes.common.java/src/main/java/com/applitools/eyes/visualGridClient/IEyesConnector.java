package com.applitools.eyes.visualGridClient;

import com.applitools.ICheckSettings;
import com.applitools.eyes.*;
import com.applitools.eyes.visualGridClient.data.*;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Future;

public interface IEyesConnector {
    void log(String massage);

    void setProxy(AbstractProxySettings proxy);

    void setLogHandler(LogHandler logHandler);

    void setServerUrl(String serverUrl) throws URISyntaxException;

    void open(String appName, String testName);

    TestResults close(boolean throwExceptionOn);

    void abortIfNotClosed();

    IResourceFuture getResource(URL url, IDownloadListener<Byte[]> iDownloadListener);

    RenderingInfo getRenderingInfo();

    Future<Boolean> renderPutResource(RunningRender runningRender, RGridResource resource);

    List<RunningRender> render(RenderRequest... renderRequests);

    RenderStatusResults getRenderStatus();

    List<RenderStatusResults> renderStatusById(String... renderIds);

    IResourceFuture createResourceFuture(RGridResource rg);

    public MatchResult matchWindow(String url, String tag, ICheckSettings checkSettings);


    void setRenderInfo(RenderingInfo renderingInfo);
}