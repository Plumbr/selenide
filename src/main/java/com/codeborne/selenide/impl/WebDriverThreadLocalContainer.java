package com.codeborne.selenide.impl;

import com.codeborne.selenide.WebDriverProvider;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.internal.Killable;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.codeborne.selenide.Configuration.*;
import static com.codeborne.selenide.WebDriverRunner.*;
import static org.openqa.selenium.remote.CapabilityType.TAKES_SCREENSHOT;

public class WebDriverThreadLocalContainer {
  protected List<WebDriver> ALL_WEB_DRIVERS = new ArrayList<WebDriver>();
  protected ThreadLocal<WebDriver> THREAD_WEB_DRIVER = new ThreadLocal<WebDriver>();

  public WebDriverThreadLocalContainer() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        closeAllWebDrivers();
      }
    });
  }

  public void setWebDriver(WebDriver webDriver) {
    closeWebDriver();
    THREAD_WEB_DRIVER.set(webDriver);
    ALL_WEB_DRIVERS.add(webDriver);
  }

  public WebDriver getWebDriver() {
    if (THREAD_WEB_DRIVER.get() == null) {
      WebDriver webDriver = createDriver();
      THREAD_WEB_DRIVER.set(webDriver);
      ALL_WEB_DRIVERS.add(webDriver);
    }
    return THREAD_WEB_DRIVER.get();
  }

  public void closeWebDriver() {
    WebDriver webdriver = THREAD_WEB_DRIVER.get();
    if (webdriver != null) {
      closeWebDriver(webdriver);
    }
  }

  protected void closeAllWebDrivers() {
    while (!ALL_WEB_DRIVERS.isEmpty()) {
      closeWebDriver(ALL_WEB_DRIVERS.get(0));
    }
  }

  protected void closeWebDriver(WebDriver webdriver) {
    THREAD_WEB_DRIVER.remove();
    ALL_WEB_DRIVERS.remove(webdriver);

    if (!holdBrowserOpen) {
      try {
        webdriver.quit();
      } catch (WebDriverException cannotCloseBrowser) {
        System.err.println("Cannot close browser normally: " + Cleanup.of.webdriverExceptionMessage(cannotCloseBrowser));
      }
      finally {
        killBrowser(webdriver);
      }
    }
  }

  protected void killBrowser(WebDriver webdriver) {
    if (webdriver instanceof Killable) {
      try {
        ((Killable) webdriver).kill();
      } catch (Exception e) {
        System.err.println("Failed to kill browser " + webdriver + ':');
        e.printStackTrace();
      }
    }
  }

  public void clearBrowserCache() {
    if (THREAD_WEB_DRIVER.get() != null) {
      THREAD_WEB_DRIVER.get().manage().deleteAllCookies();
    }
  }

  public String getPageSource() {
    return getWebDriver().getPageSource();
  }

  public String getCurrentUrl() {
    return getWebDriver().getCurrentUrl();
  }

  protected WebDriver createDriver() {
    if (remote != null) {
      return createRemoteDriver(remote, browser);
    }
    else if (CHROME.equalsIgnoreCase(browser)) {
      ChromeOptions options = new ChromeOptions();
      if (startMaximized) {
        // Due do bug in ChromeDriver we need this workaround
        // http://stackoverflow.com/questions/3189430/how-do-i-maximize-the-browser-window-using-webdriver-selenium-2
        options.addArguments("chrome.switches", "--start-maximized");
      }
      return new ChromeDriver(options);
    }
    else if (ie()) {
      return maximize(new InternetExplorerDriver());
    }
    else if (htmlUnit()) {
      DesiredCapabilities capabilities = DesiredCapabilities.htmlUnit();
      capabilities.setCapability(HtmlUnitDriver.INVALIDSELECTIONERROR, true);
      capabilities.setCapability(HtmlUnitDriver.INVALIDXPATHERROR, false);
      capabilities.setJavascriptEnabled(true);
      if (browser.indexOf(':') > -1) {
        // Use constants BrowserType.IE, BrowserType.FIREFOX, BrowserType.CHROME etc.
        String emulatedBrowser = browser.replaceFirst("htmlunit:(.*)", "$1");
        capabilities.setVersion(emulatedBrowser);
      }
      return new HtmlUnitDriver(capabilities);
    }
    else if (FIREFOX.equalsIgnoreCase(browser)) {
      return maximize(new FirefoxDriver());
    }
    else if (OPERA.equalsIgnoreCase(browser)) {
      return createInstanceOf("com.opera.core.systems.OperaDriver");
    }
    else if (PHANTOMJS.equals(browser)) {
      DesiredCapabilities capabilities = new DesiredCapabilities();
      capabilities.setJavascriptEnabled(true);
      capabilities.setCapability(TAKES_SCREENSHOT, true);
      return maximize(new org.openqa.selenium.phantomjs.PhantomJSDriver(capabilities));
    }
    else {
      return createInstanceOf(browser);
    }
  }

  protected RemoteWebDriver maximize(RemoteWebDriver driver) {
    if (startMaximized) {
      driver.manage().window().maximize();
    }
    return driver;
  }

  protected WebDriver createInstanceOf(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      if (WebDriverProvider.class.isAssignableFrom(clazz)) {
        return ((WebDriverProvider)clazz.newInstance()).createDriver();
      } else {
        return (WebDriver) Class.forName(className).newInstance();
      }
    }
    catch (Exception invalidClassName) {
      throw new IllegalArgumentException(invalidClassName);
    }
  }

  protected WebDriver createRemoteDriver(String remote, String browser) {
    try {
      DesiredCapabilities capabilities = new DesiredCapabilities();
      capabilities.setBrowserName(browser);
      return new RemoteWebDriver(new URL(remote), capabilities);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid 'remote' parameter: " + remote, e);
    }
  }
}
