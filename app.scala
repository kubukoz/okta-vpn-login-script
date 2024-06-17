//> using options -Wunused:imports
//> using dependency org.seleniumhq.selenium:selenium-java:4.20.0
//> using toolkit default
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.Using
import scala.util.Using.Releasable

given Releasable[WebDriver] = _.close()

type Remote[A] = WebDriver ?=> A

def driver(using d: WebDriver): d.type = d

@main def main(url: String) = {

  val username   = sys.env("VPN_USERNAME")
  val pass       = sys.env("VPN_PASSWORD")
  val browserKey = sys.env.getOrElse("VPN_LOGIN_BROWSER", "FIREFOX").toUpperCase()

  val browsers = Map[String, () => WebDriver](
    "CHROME"  -> (() => ChromeDriver()),
    "FIREFOX" -> (() => FirefoxDriver()),
  )

  val pageText = Using.resource(browsers(browserKey)()) { driver =>
    given WebDriver = driver

    driver.get(url)

    waitForElement(By.cssSelector("input[name=identifier]")).sendKeys(username)
    waitForElement(By.cssSelector("input[type=submit]")).click()
    waitForElement(By.cssSelector("input[type=password]")).sendKeys(pass)
    waitForElement(By.cssSelector("input[type=submit]")).click()

    // This could be more easily done by checking the URL, but the XPath way means I don't need the URL in my source code or any external config.
    waitForElement(By.xpath("//body[text()='Login Successful!']"), rate = 1.second)

    driver.getPageSource()
  }

  pageText match {
    case s"$_<prelogin-cookie>$cookie</prelogin-cookie>$_" => println(cookie)
  }
}

def waitForElement(locator: By, rate: Duration = 100.millis): Remote[WebElement] = {
  @tailrec
  def findEl(): WebElement =
    try driver.findElement(locator)
    catch {
      case nsee: org.openqa.selenium.NoSuchElementException =>
        System.err.println(s"Waiting for element to appear: $locator")
        Thread.sleep(rate.toMillis)
        findEl()
    }

  val el = findEl()

  while (!el.isDisplayed()) {
    System.err.println(s"Waiting for element to be displayed... $locator")
    Thread.sleep(rate.toMillis)
  }
  el
}
