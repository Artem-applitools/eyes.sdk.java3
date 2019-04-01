package com.applitools.eyes.visualgridclient.services;

import com.applitools.ICheckSettings;
import com.applitools.eyes.*;
import com.applitools.eyes.selenium.IConfigurationGetter;
import com.applitools.eyes.visualgridclient.model.*;
import com.applitools.utils.GeneralUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class VisualGridTask implements Callable<TestResultContainer>, CompletableTask {


    private final Logger logger;

    private boolean isSent;
    private boolean isThrowException;

    public enum TaskType {OPEN, CHECK, CLOSE, ABORT}

    private IConfigurationGetter configurationGetter;
    private TestResults testResults;

    private IEyesConnector eyesConnector;
    private TaskType type;

    private RenderStatusResults renderResult;
    private List<TaskListener> listeners = new ArrayList<>();
    private ICheckSettings checkSettings;

    private RunningTest runningTest;
    private Error exception;
    private RenderingTask renderingTask = null;
    private AtomicBoolean isTaskComplete = new AtomicBoolean(false);
    private final List<VisualGridSelector[]> regionSelectors;

    interface TaskListener {

        void onTaskComplete(VisualGridTask visualGridTask);

        void onTaskFailed(Error e, VisualGridTask visualGridTask);

        void onRenderComplete(RenderingTask renderTask, Error e);

    }

    public VisualGridTask(IConfigurationGetter seleniumConfigurationProvider, TestResults testResults, IEyesConnector eyesConnector, TaskType type, TaskListener runningTestListener,
                          ICheckSettings checkSettings, RunningTest runningTest, List<VisualGridSelector[]> regionSelectors, boolean throwException) {
        this.configurationGetter = seleniumConfigurationProvider;
        this.testResults = testResults;
        this.eyesConnector = eyesConnector;
        this.type = type;
        this.regionSelectors = regionSelectors;
        this.listeners.add(runningTestListener);
        this.logger = runningTest.getLogger();
        this.checkSettings = checkSettings;
        this.runningTest = runningTest;
        this.isThrowException = throwException;
    }

    public RenderBrowserInfo getBrowserInfo() {
        return runningTest.getBrowserInfo();
    }

    public TaskType getType() {
        return type;
    }

    boolean isSent() {
        return isSent;
    }

    void setIsSent() {
        this.isSent = true;
    }

    @Override
    public TestResultContainer call() {
        try {
            testResults = null;
            switch (type) {
                case OPEN:
                    logger.verbose("VisualGridTask.run opening task");
                    String userAgent = renderResult.getUserAgent();
                    RectangleSize deviceSize = renderResult.getDeviceSize();
                    eyesConnector.setUserAgent(userAgent);
                    eyesConnector.setDeviceSize(deviceSize);
                    eyesConnector.open(configurationGetter);
                    logger.verbose("Eyes Open Done.");
                    break;

                case CHECK:
                    logger.verbose("VisualGridTask.run check task");

                    String imageLocation = renderResult.getImageLocation();
                    String domLocation = renderResult.getDomLocation();
                    List<Region> regions = renderResult.getSelectorRegions();
                    if (imageLocation == null) {
                        logger.verbose("CHECKING IMAGE WITH NULL LOCATION - ");
                        logger.verbose(renderResult.toString());
                    }
                    Location location = null;
                    if (regionSelectors.size() > 0) {
                        VisualGridSelector[] targetSelector = regionSelectors.get(regionSelectors.size() - 1);
                        if (targetSelector.length > 0 && "target".equals(targetSelector[0].getCategory())) {
                            location = regions.get(regions.size() - 1).getLocation();
                        }
                    }

                    eyesConnector.matchWindow(imageLocation, domLocation, checkSettings, regions, this.regionSelectors, location);
                    logger.verbose("match done");
                    break;

                case CLOSE:
                    logger.verbose("VisualGridTask.run close task");
                    testResults = eyesConnector.close(this.isThrowException);
                    logger.verbose("Eyes Close Done.");
                    break;

                case ABORT:
                    logger.verbose("VisualGridTask.run abort task");
                    if (runningTest.isTestOpen()) {
                        testResults = eyesConnector.abortIfNotClosed();
                    } else {
                        logger.verbose("Closing a not opened test");
                    }
            }
            @SuppressWarnings("UnnecessaryLocalVariable")
            TestResultContainer testResultContainer = new TestResultContainer(testResults, this.exception);
            notifySuccessAllListeners();
            this.isTaskComplete.set(true);
            return testResultContainer;
        } catch (Throwable e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
            this.exception = new Error(e);
            notifyFailureAllListeners(new Error(e));
        }
        return null;
    }

    private void notifySuccessAllListeners() {
        for (TaskListener listener : listeners) {
            listener.onTaskComplete(this);
        }
    }

    private void notifyFailureAllListeners(Error e) {
        for (TaskListener listener : listeners) {
            listener.onTaskFailed(e, this);
        }
    }

    private void notifyRenderCompleteAllListeners() {
        for (TaskListener listener : listeners) {
            listener.onRenderComplete(this.renderingTask, exception);
        }
    }

    public IEyesConnector getEyesConnector() {
        return eyesConnector;
    }

    public void setRenderResult(RenderStatusResults renderResult) {
        logger.verbose("enter");
        this.renderResult = renderResult;
        notifyRenderCompleteAllListeners();
        logger.verbose("exit");
    }

    public boolean isTaskReadyToCheck() {
        return this.renderResult != null;
    }

    public RunningTest getRunningTest() {
        return runningTest;
    }

    public boolean getIsTaskComplete() {
        return isTaskComplete.get();
    }

    public void addListener(TaskListener listener) {
        this.listeners.add(listener);
    }

    public void setRenderError(String renderId, String error) {
        logger.verbose("enter - renderId: " + renderId);
        for (TaskListener listener : listeners) {
            exception = new Error("Render Failed for " + this.getBrowserInfo() + " (renderId: " + renderId + ") with reason: " + error);
            listener.onTaskFailed(exception, this);
        }
        logger.verbose("exit - renderId: " + renderId);
    }

    public Error getException() {
        return exception;
    }

    public void setException(Error exception) {
        logger.verbose("aborting task with exception");
        this.exception = exception;
        this.type = TaskType.ABORT;
    }

    @Override
    public String toString() {
        return "VisualGridTask - Type: " + type + " ; Browser Info: " + getBrowserInfo();
    }

    public void setRenderingTask(RenderingTask renderingTask) {
        this.renderingTask = renderingTask;
    }

    public RunningSession getSession(){
        return this.eyesConnector.getSession();
    }

}

