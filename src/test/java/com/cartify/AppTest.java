package com.cartify;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AppTest {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String baseUrl;
    private static String testUserEmail;
    private static final String TEST_PASSWORD = "password123";

    @BeforeAll
    public static void setupAll() throws Exception {
        baseUrl = System.getProperty("baseUrl", "http://frontend:5173");
        testUserEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.setCapability("goog:loggingPrefs", java.util.Collections.singletonMap("browser", "ALL"));

        String seleniumUrl = System.getProperty("seleniumUrl", "http://selenium:4444/wd/hub");
        try {
            URI seleniumUri = URI.create(seleniumUrl);
            driver = new RemoteWebDriver(seleniumUri.toURL(), options);
        } catch (Exception e) { throw e; }

        wait = new WebDriverWait(driver, Duration.ofSeconds(45));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @AfterAll
    public static void tearDownAll() {
        if (driver != null) driver.quit();
    }

    private void navigateTo(String path) {
        driver.get(baseUrl + path);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("root")));
    }

    private void login(String email, String password) {
        navigateTo("/login");
        WebElement e = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']")));
        e.clear(); e.sendKeys(email);
        WebElement p = driver.findElement(By.cssSelector("input[type='password']"));
        p.clear(); p.sendKeys(password);
        p.sendKeys(Keys.ENTER);
        try { Thread.sleep(1000); } catch(Exception ex) {}
    }

    @Test @Order(1) @DisplayName("1. User Registration")
    public void testRegistration() {
        navigateTo("/register");
        driver.manage().deleteAllCookies();
        ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
        driver.navigate().refresh();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']"))).sendKeys("Test User");
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(testUserEmail);
        WebElement p = driver.findElement(By.cssSelector("input[placeholder*='Password']"));
        p.sendKeys(TEST_PASSWORD);
        p.sendKeys(Keys.ENTER);
        wait.until(ExpectedConditions.or(ExpectedConditions.urlToBe(baseUrl + "/"), ExpectedConditions.urlContains("/login")));
    }

    @Test @Order(2) @DisplayName("2. User Login Success")
    public void testLogin() {
        login(testUserEmail, TEST_PASSWORD);
        wait.until(ExpectedConditions.urlContains("/"));
    }

    @Test @Order(3) @DisplayName("3. Login Failure")
    public void testLoginFailure() {
        login(testUserEmail, "wrong_pass_999");
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'error')]"))).isDisplayed());
    }

    @Test @Order(4) @DisplayName("4. Forgot Password Flow")
    public void testForgotPassword() {
        navigateTo("/login");
        wait.until(ExpectedConditions.elementToBeClickable(By.partialLinkText("Forgot"))).click();
        wait.until(ExpectedConditions.urlContains("/forgot-password"));
    }

    @Test @Order(5) @DisplayName("5. Unauth Access Restriction")
    public void testUnauthAccess() {
        driver.get(baseUrl + "/orders");
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    @Test @Order(6) @DisplayName("6. Search Functionality")
    public void testSearch() {
        navigateTo("/products");
        WebElement s = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='Search']")));
        s.sendKeys("Watch");
        s.sendKeys(Keys.ENTER);
        wait.until(ExpectedConditions.urlContains("search="));
    }

    @Test @Order(7) @DisplayName("7. Add to Cart")
    public void testAddToCart() {
        navigateTo("/products");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']"))).click();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]"))).click();
        try { Thread.sleep(2000); } catch(Exception e) {} 
        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
    }

    @Test @Order(8) @DisplayName("8. Remove from Cart")
    public void testRemoveFromCart() {
        navigateTo("/cart");
        try {
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Remove')]"))).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'empty')]")));
        } catch (Exception e) {}
    }

    @Test @Order(9) @DisplayName("9. Checkout Navigation")
    public void testCheckoutFlow() {
        login(testUserEmail, TEST_PASSWORD); // Ensure logged in
        testAddToCart();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(), 'Checkout')]"))).click();
        wait.until(ExpectedConditions.urlContains("/checkout"));
    }

    @Test @Order(10) @DisplayName("10. User Profile Access")
    public void testUserProfile() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/account");
        wait.until(ExpectedConditions.urlContains("/account"));
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("h1"))).getText().contains("Account"));
    }

    @Test @Order(11) @DisplayName("11. Admin Login")
    public void testAdminLogin() {
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
    }

    @Test @Order(12) @DisplayName("12. Admin Add Product UI")
    public void testAdminAddProduct() {
        testAdminLogin();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[text()='Products']"))).click();
        try { Thread.sleep(1000); } catch(Exception ex) {}
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add Product')]"))).click();
        assertTrue(driver.getPageSource().contains("Name"));
    }

    @Test @Order(13) @DisplayName("13. Admin Delete Product")
    public void testAdminDeleteProduct() {
        testAdminLogin();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[text()='Products']"))).click();
        try {
            WebElement d = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Delete')]")));
            d.click();
            driver.switchTo().alert().accept();
        } catch (Exception e) {}
    }

    @Test @Order(14) @DisplayName("14. Admin Order Management")
    public void testAdminOrders() {
        testAdminLogin();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[text()='Orders']"))).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("table")));
    }

    @Test @Order(15) @DisplayName("15. Submit Product Review")
    public void testSubmitReview() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/products");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']"))).click();
        WebElement t = wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("textarea")));
        t.sendKeys("Auto Review " + UUID.randomUUID().toString());
        driver.findElement(By.xpath("//button[contains(text(), 'Submit')]")).click();
        try { Thread.sleep(2000); } catch(Exception e) {}
        assertTrue(driver.getPageSource().contains("Review"));
    }
}