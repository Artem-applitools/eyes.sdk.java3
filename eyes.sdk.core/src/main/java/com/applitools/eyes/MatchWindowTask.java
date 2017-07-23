/*
 * Applitools SDK for Selenium integration.
 */
package com.applitools.eyes;

import com.applitools.utils.ArgumentGuard;
import com.applitools.utils.GeneralUtils;

import java.awt.image.BufferedImage;

public class MatchWindowTask {

    private static final int MATCH_INTERVAL = 500; // Milliseconds

    private final Logger logger;
    private final ServerConnector serverConnector;
    private final RunningSession runningSession;
    private final int defaultRetryTimeout;
    private final AppOutputProvider appOutputProvider;

    private EyesScreenshot lastScreenshot = null;
    private MatchResult matchResult;
    private Region lastScreenshotBounds;

    /**
     * @param logger            A logger instance.
     * @param serverConnector   Our gateway to the agent
     * @param runningSession    The running session in which we should match the window
     * @param retryTimeout      The default total time to retry matching (ms).
     * @param appOutputProvider A callback for getting the application output when performing match.
     */
    public MatchWindowTask(Logger logger, ServerConnector serverConnector,
                           RunningSession runningSession, int retryTimeout,
                           AppOutputProvider appOutputProvider) {
        ArgumentGuard.notNull(serverConnector, "serverConnector");
        ArgumentGuard.notNull(runningSession, "runningSession");
        ArgumentGuard.greaterThanOrEqualToZero(retryTimeout, "retryTimeout");
        ArgumentGuard.notNull(appOutputProvider, "appOutputProvider");

        this.logger = logger;
        this.serverConnector = serverConnector;
        this.runningSession = runningSession;
        this.defaultRetryTimeout = retryTimeout;
        this.appOutputProvider = appOutputProvider;
    }

    /**
     * Creates the match data and calls the server connector matchWindow method.
     * @param userInputs         The user inputs related to the current appOutput.
     * @param appOutput          The application output to be matched.
     * @param tag                Optional tag to be associated with the match (can
     *                           be {@code null}).
     * @param ignoreMismatch     Whether to instruct the server to ignore the
     *                           match attempt in case of a mismatch.
     * @param imageMatchSettings The settings to use.
     * @return The match result.
     */
    protected MatchResult performMatch(Trigger[] userInputs,
                                       AppOutputWithScreenshot appOutput,
                                       String tag, boolean ignoreMismatch,
                                       ImageMatchSettings imageMatchSettings) {

        // Prepare match data.
        MatchWindowData data = new MatchWindowData(userInputs, appOutput
                .getAppOutput(), tag, ignoreMismatch,
                new MatchWindowData.Options(tag, userInputs, ignoreMismatch,
                        false, false, false,
                        imageMatchSettings));

        // Perform match.
        return serverConnector.matchWindow(runningSession, data);
    }

    /**
     * Repeatedly obtains an application snapshot and matches it with the next
     * expected output, until a match is found or the timeout expires.
     * @param userInputs                        User input preceding this match.
     * @param lastScreenshot                    The last screenshot matched or not ignored.
     * @param regionProvider                    Window region to capture.
     * @param tag                               Optional tag to be associated with the match (can be {@code null}).
     * @param shouldMatchWindowRunOnceOnTimeout Force a single match attempt at the end of the match timeout.
     * @param ignoreMismatch                    Whether to instruct the server to ignore the match attempt in case
     *                                          of a mismatch.
     * @param imageMatchSettings                The settings to use.
     * @param retryTimeout                      The amount of time to retry matching in milliseconds or a
     *                                          negative value to use the default retry timeout.
     * @return Returns the results of the match
     */
    public MatchResult matchWindow(Trigger[] userInputs,
                                   EyesScreenshot lastScreenshot,
                                   RegionProvider regionProvider, String tag,
                                   boolean shouldMatchWindowRunOnceOnTimeout,
                                   boolean ignoreMismatch,
                                   ImageMatchSettings imageMatchSettings,
                                   int retryTimeout) {

        if (retryTimeout < 0) {
            retryTimeout = defaultRetryTimeout;
        }

        logger.verbose(String.format("retryTimeout = %d", retryTimeout));

        EyesScreenshot screenshot = takeScreenshot(userInputs, regionProvider, tag,
                shouldMatchWindowRunOnceOnTimeout, ignoreMismatch, imageMatchSettings, retryTimeout);

        if (ignoreMismatch) {
            return matchResult;
        }

        updateLastScreenshot(screenshot);
        updateBounds(regionProvider.getRegion());

        return matchResult;
    }

    private EyesScreenshot takeScreenshot(Trigger[] userInputs, RegionProvider regionProvider,
                                          String tag, boolean shouldMatchWindowRunOnceOnTimeout, boolean ignoreMismatch,
                                          ImageMatchSettings imageMatchSettings, int retryTimeout) {
        long elapsedTimeStart = System.currentTimeMillis();
        EyesScreenshot screenshot = null;
        AppOutputWithScreenshot appOutput = null;

        // If the wait to load time is 0, or "run once" is true,
        // we perform a single check window.
        if (0 == retryTimeout || shouldMatchWindowRunOnceOnTimeout) {

            if (shouldMatchWindowRunOnceOnTimeout) {
                GeneralUtils.sleep(retryTimeout);
            }
            screenshot = tryTakeScreenshot(userInputs, regionProvider, tag, ignoreMismatch, imageMatchSettings);
        } else {
            screenshot = retryTakingScreenshot(userInputs, regionProvider, tag, ignoreMismatch, imageMatchSettings, retryTimeout);
        }

        double elapsedTime = (System.currentTimeMillis() - elapsedTimeStart) / 1000;
        logger.verbose(String.format("Completed in %.2f seconds", elapsedTime));
        matchResult.setScreenshot(screenshot);
        return screenshot;
    }

    private EyesScreenshot retryTakingScreenshot(Trigger[] userInputs, RegionProvider regionProvider, String tag, boolean ignoreMismatch,
                                                 ImageMatchSettings imageMatchSettings, int retryTimeout) {
        /*
        * We call a "tolerant" match window until we find a match
        * or we timeout, in which case we call a single "strict"
        * match.
        */

        // We intentionally start the timer after(!) taking the screenshot,
        // so less time is "wasted" on the transfer of the image.
        AppOutputWithScreenshot appOutput = appOutputProvider.getAppOutput(regionProvider, lastScreenshot);

        // Start the retry timer.
        long start = System.currentTimeMillis();

        matchResult = performMatch(userInputs, appOutput, tag, true, imageMatchSettings);

        EyesScreenshot screenshot = null;

        long retry = System.currentTimeMillis() - start;

        // The match retry loop.
        while ((retry < retryTimeout) && !matchResult.getAsExpected()) {

            // Wait before trying again.
            GeneralUtils.sleep(MATCH_INTERVAL);

            screenshot = tryTakeScreenshot(userInputs, regionProvider, tag, true, imageMatchSettings);

            retry = System.currentTimeMillis() - start;
        }

        // if we're here because we haven't found a match yet, try once more
        if (!matchResult.getAsExpected()) {
            screenshot = tryTakeScreenshot(userInputs, regionProvider, tag, ignoreMismatch, imageMatchSettings);
        }
        return screenshot;
    }

    private EyesScreenshot tryTakeScreenshot(Trigger[] userInputs, RegionProvider regionProvider, String tag,
                                             boolean ignoreMismatch, ImageMatchSettings imageMatchSettings) {
        AppOutputWithScreenshot appOutput;
        appOutput = appOutputProvider.getAppOutput(regionProvider, lastScreenshot);

        // Notice the ignoreMismatch here is true
        matchResult = performMatch(userInputs, appOutput, tag, ignoreMismatch, imageMatchSettings);
        return appOutput.getScreenshot();
    }

    private void updateLastScreenshot(EyesScreenshot screenshot) {
        if (screenshot != null) {
            lastScreenshot = screenshot;
        }
    }

    private void updateBounds(Region region) {
        if (region.isEmpty()) {
            if (lastScreenshot == null) {
                // We set an "infinite" image size since we don't know what the screenshot size is...
                lastScreenshotBounds = new Region(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
            } else {
                BufferedImage image = lastScreenshot.getImage();
                lastScreenshotBounds = new Region(0, 0, image.getWidth(), image.getHeight());
            }
        } else {
            lastScreenshotBounds = region;
        }
    }

    public Region getLastScreenshotBounds() {
        return lastScreenshotBounds;
    }

}
