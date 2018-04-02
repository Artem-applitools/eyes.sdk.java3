package com.applitools.eyes.selenium;

import com.applitools.eyes.*;
import com.applitools.eyes.metadata.ImageMatchSettings;
import com.applitools.eyes.metadata.ActualAppOutput;
import com.applitools.eyes.metadata.SessionResults;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

@RunWith(Parameterized.class)
public abstract class TestSetup {

    private static LogHandler logHandler;

    protected static Eyes eyes;
    protected static WebDriver driver;
    protected static WebDriver webDriver;

    protected static String testSuitName;

    protected static String testedPageUrl = "http://applitools.github.io/demo/TestPages/FramesTestPage/";
    protected static RectangleSize testedPageSize = new RectangleSize(800, 600);

    protected static boolean forceFullPageScreenshot = false;
    protected static boolean hideScrollbars = true;
    protected static Capabilities caps;
    protected static DesiredCapabilities desiredCaps = new DesiredCapabilities();
    protected static BatchInfo batchInfo = new BatchInfo("Java3 Tests");

    private HashSet<FloatingMatchSettings> expectedFloatingRegions = new HashSet<>();
    private HashSet<Region> expectedIgnoreRegions = new HashSet<>();

    @Parameterized.Parameters(name="{0}")
    public static Collection<String> data() {
        String testPlatforms = System.getenv("APPLITOOLS_TEST_PLATFORMS");
        if (testPlatforms == null || testPlatforms.isEmpty()) {
            testPlatforms = System.getProperty("os.name");
        }
        return Arrays.asList(testPlatforms.split(";"));
        //"Windows 10"/*, "Linux", "macOS 10.13"*/);
    }

    @BeforeClass
    public static void OneTimeSetUp() {

        // Initialize the eyes SDK and set your private API key.
        eyes = new Eyes();
//        eyes.setServerUrl(URI.create("https://localhost.applitools.com"));

        //logHandler = new FileLogger("c:\\temp\\logs\\TestElement.log", true, true);
        logHandler = new StdoutLogHandler(true);
        //eyes.setLogHandler(logHandler);
        eyes.setForceFullPageScreenshot(forceFullPageScreenshot);
        eyes.setStitchMode(StitchMode.CSS);

        eyes.setHideScrollbars(true);

        if (System.getenv("CI") == null) {
            eyes.setDebugScreenshotsPath("c:\\temp\\logs");
            eyes.setSaveDebugScreenshots(true);
        }

        //batchInfo = new BatchInfo(testSuitName);
        String batchId = System.getenv("APPLITOOLS_BATCH_ID");
        if (batchId != null) {
            batchInfo.setId(batchId);
        }

        eyes.setBatch(batchInfo);
    }

    protected void setExpectedIgnoreRegions(Region... expectedIgnoreRegions) {
        this.expectedIgnoreRegions = new HashSet<>(Arrays.asList(expectedIgnoreRegions));
    }

    protected void setExpectedFloatingsRegions(FloatingMatchSettings... expectedFloatingsRegions) {
        this.expectedFloatingRegions = new HashSet<>(Arrays.asList(expectedFloatingsRegions));
    }

    @Parameterized.Parameter
    public String platform;

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {

            String seleniumServerUrl = System.getenv("SELENIUM_SERVER_URL");
            if (seleniumServerUrl.equalsIgnoreCase("http://ondemand.saucelabs.com/wd/hub")) {
                desiredCaps.setCapability("username", System.getenv("SAUCE_USERNAME"));
                desiredCaps.setCapability("accesskey", System.getenv("SAUCE_ACCESS_KEY"));
                desiredCaps.setCapability("platform", platform);
                caps.merge(desiredCaps);
            }

            try {
                webDriver = new RemoteWebDriver(new URL(seleniumServerUrl), caps);
            } catch (MalformedURLException ex) {
            }

            String fps = eyes.getForceFullPageScreenshot() ? "_FPS" : "";
            String testName = description.getMethodName() + fps;
            testName = testName.replace('[','_')
                               .replace(' ','_')
                               .replace("]","");

            driver = eyes.open(webDriver,
                    testSuitName,
                    testName,
                    testedPageSize
            );

            driver.get(testedPageUrl);

            eyes.setDebugScreenshotsPrefix("Java_" + testName + "_");

            expectedIgnoreRegions.clear();
            expectedFloatingRegions.clear();
        }

        protected void finished(Description description) {
            try {
                TestResults results = eyes.close();
                String apiSessionUrl = results.getApiUrls().getSession();
                URI apiSessionUri = UriBuilder.fromUri(apiSessionUrl)
                        .queryParam("format", "json")
                        .queryParam("AccessToken", results.getSecretToken())
                        .queryParam("apiKey", eyes.getApiKey())
                        .build();

                Client client = ClientBuilder.newClient();
                String srStr = client.target(apiSessionUri)
                        .request(MediaType.APPLICATION_JSON)
                        .get(String.class);

                ObjectMapper jsonMapper = new ObjectMapper();
                jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

                SessionResults resultObject = jsonMapper.readValue(srStr, SessionResults.class);

                ActualAppOutput[] actualAppOutput = resultObject.getActualAppOutput();

                ImageMatchSettings imageMatchSettings = actualAppOutput[0].getImageMatchSettings();
                FloatingMatchSettings[] floating = imageMatchSettings.getFloating();
                Region[] ignoreRegions = imageMatchSettings.getIgnore();

                if (expectedFloatingRegions.size() > 0) {
                    HashSet<FloatingMatchSettings> floatingRegionsSet = new HashSet<>(Arrays.asList(floating));
                    Assert.assertTrue("Floating regions lists differ", CollectionUtils.isEqualCollection(expectedFloatingRegions, floatingRegionsSet));
                }

                if (expectedIgnoreRegions.size() > 0) {
                    HashSet<Region> ignoreRegionsSet = new HashSet<>(Arrays.asList(ignoreRegions));
                    Assert.assertTrue("Ignore regions lists differ", CollectionUtils.isEqualCollection(expectedIgnoreRegions, ignoreRegionsSet));
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                eyes.abortIfNotClosed();
                driver.quit();
            }
        }
    };

    @Rule
    public ExpectedException thrown = ExpectedException.none();
}
