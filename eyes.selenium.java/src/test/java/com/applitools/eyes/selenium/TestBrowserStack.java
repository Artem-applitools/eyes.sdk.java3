package com.applitools.eyes.selenium;

import com.applitools.eyes.selenium.fluent.Target;
import com.applitools.eyes.utils.TestUtils;
import com.applitools.utils.GeneralUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static com.applitools.eyes.selenium.TestsDataProvider.BROWSERSTACK_ACCESS_KEY;
import static com.applitools.eyes.selenium.TestsDataProvider.BROWSERSTACK_SELENIUM_URL;
import static com.applitools.eyes.selenium.TestsDataProvider.BROWSERSTACK_USERNAME;

public class TestBrowserStack {

    @Test
    public void TestIE11() {

        Eyes eyes = new Eyes();
        TestUtils.setupLogging(eyes);

        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("browser", "IE");
        caps.setCapability("browser_version", "11.0");
        caps.setCapability("os", "Windows");
        caps.setCapability("os_version", "10");
        caps.setCapability("resolution", "1024x768");
        caps.setCapability("browserstack.user", BROWSERSTACK_USERNAME);
        caps.setCapability("browserstack.key", BROWSERSTACK_ACCESS_KEY);

        WebDriver driver = null;
        try {
            driver = new RemoteWebDriver(new URL(BROWSERSTACK_SELENIUM_URL), caps);
            driver.get("https://applitools.github.io/demo/TestPages/FramesTestPage/");
            eyes.open(driver, "TestBrowserStack", "TesIE11");
            eyes.check("viewport", Target.window().fully(false).sendDom(false));
            eyes.check("full page", Target.window().fully(true).sendDom(false));
            eyes.close();
        } catch (MalformedURLException e) {
            GeneralUtils.logExceptionStackTrace(eyes.getLogger(), e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

}
