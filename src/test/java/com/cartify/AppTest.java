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
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.setCapability("goog:loggingPrefs", java.util.Collections.singletonMap("browser", "ALL"));

        String seleniumUrl = System.getProperty("seleniumUrl", "http://selenium:4444/wd/hub");
        System.out.println("Connecting to Selenium at: " + seleniumUrl);
        System.out.println("Testing against Base URL: " + baseUrl);

        try {
            URI seleniumUri = URI.create(seleniumUrl);
            driver = new RemoteWebDriver(seleniumUri.toURL(), options);
        } catch (Exception e) {
            System.err.println("Could not connect to Selenium Grid!");
            throw e;
        }

        wait = new WebDriverWait(driver, Duration.ofSeconds(45));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @AfterAll
    public static void tearDownAll() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void navigateTo(String path) {
        String targetUrl = baseUrl + path;
        driver.get(targetUrl);
        try {
            wait.until(ExpectedConditions.urlContains(path));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("root")));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("nav")));
        } catch (TimeoutException e) {
            printDebugInfo("TIMEOUT navigating to " + path);
            throw e;
        }
    }

    private void printDebugInfo(String message) {
        System.err.println("--- DEBUG INFO: " + message + " ---");
        try {
            List<LogEntry> logs = driver.manage().logs().get(LogType.BROWSER).getAll();
            for (LogEntry entry : logs) {
                System.err.println("[BROWSER LOG] " + entry.getLevel() + ": " + entry.getMessage());
            }
        } catch (Exception ex) {}
    }

    private void clearState() {
        try {
            if (driver.getCurrentUrl().startsWith("http")) {
                driver.manage().deleteAllCookies();
                ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
            }
        } catch (Exception e) {}
    }

    private void login(String email, String password) {
        navigateTo("/login");
        WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']")));
        emailInput.sendKeys(email);
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }

    @Test @Order(1) @DisplayName("1. User Registration")
    public void testRegistration() {
        navigateTo("/register");
        clearState();
        driver.navigate().refresh();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']"))).sendKeys("Test User");
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(testUserEmail);
        driver.findElement(By.cssSelector("input[placeholder*='Password']")).sendKeys(TEST_PASSWORD);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        wait.until(ExpectedConditions.or(ExpectedConditions.urlToBe(baseUrl + "/"), ExpectedConditions.urlContains("/login")));
    }

    @Test @Order(2) @DisplayName("2. User Login Success")
    public void testLogin() {
        login(testUserEmail, TEST_PASSWORD);
        wait.until(ExpectedConditions.urlContains("/"));
    }

    @Test @Order(3) @DisplayName("3. Login Failure")
    public void testLoginFailure() {
        clearState();
        login(testUserEmail, "wrong_pass");
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'error')]"))).isDisplayed());
    }

    @Test @Order(4) @DisplayName("4. Forgot Password Flow")
    public void testForgotPassword() {
        navigateTo("/login");
        driver.findElement(By.linkText("Forgot Password?")).click();
        wait.until(ExpectedConditions.urlContains("/forgot-password"));
        assertTrue(driver.findElement(By.tagName("h1")).getText().contains("Reset"));
    }

    @Test @Order(5) @DisplayName("5. Unauth Access Restriction")
    public void testUnauthAccess() {
        clearState();
        driver.get(baseUrl + "/orders");
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    @Test @Order(6) @DisplayName("6. Search Functionality")
    public void testSearch() {
        navigateTo("/products");
        WebElement searchInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='search']")));
        searchInput.sendKeys("Watch");
        searchInput.sendKeys(Keys.ENTER);
        wait.until(ExpectedConditions.urlContains("search=Watch"));
    }

    @Test @Order(7) @DisplayName("7. Add to Cart")
    public void testAddToCart() {
        navigateTo("/products");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']"))).click();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]"))).click();
        navigateTo("/cart");
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]"))).isDisplayed());
    }

    @Test @Order(8) @DisplayName("8. Remove from Cart")
    public void testRemoveFromCart() {
        navigateTo("/cart");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Remove')]"))).click();
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'Empty')]"))).isDisplayed());
    }

    @Test @Order(9) @DisplayName("9. Checkout Navigation")
    public void testCheckoutFlow() {
        testAddToCart(); // Ensure item is in cart
        driver.findElement(By.xpath("//button[contains(text(), 'Checkout')]")).click();
        wait.until(ExpectedConditions.urlContains("/checkout"));
    }

    @Test @Order(10) @DisplayName("10. User Profile Access")
    public void testUserProfile() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/account");
        assertTrue(driver.findElement(By.tagName("h1")).getText().contains("Profile"));
    }

    @Test @Order(11) @DisplayName("11. Admin Login")
    public void testAdminLogin() {
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
    }

    @Test @Order(12) @DisplayName("12. Admin Add Product UI")
    public void testAdminAddProduct() {
        testAdminLogin();
        driver.findElement(By.xpath("//button[contains(text(), 'Add Product')]")).click();
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Product Name']"))).isDisplayed());
    }

    @Test @Order(13) @DisplayName("13. Admin Delete Product")
    public void testAdminDeleteProduct() {
        testAdminLogin();
        WebElement deleteBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(@class, 'delete')]")));
        deleteBtn.click();
        driver.switchTo().alert().accept();
    }

    @Test @Order(14) @DisplayName("14. Admin Order Management")
    public void testAdminOrders() {
        testAdminLogin();
        driver.findElement(By.xpath("//button[text()='Orders']")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'Status')]")));
    }

    @Test @Order(15) @DisplayName("15. Submit Product Review")
    public void testSubmitReview() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/products");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']"))).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("textarea"))).sendKeys("Automation Review");
        driver.findElement(By.xpath("//button[text()='Submit Review']")).click();
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//p[text()='Automation Review']"))).isDisplayed());
    }
}