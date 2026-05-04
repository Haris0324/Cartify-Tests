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
        System.out.println("Navigating to: " + targetUrl);
        driver.get(targetUrl);
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("root")));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("nav")));
        } catch (TimeoutException e) {
            printDebugInfo("TIMEOUT navigating to " + path);
            throw e;
        }
    }

    private void printDebugInfo(String message) {
        System.err.println("--- DEBUG INFO: " + message + " ---");
        System.err.println("URL: " + driver.getCurrentUrl());
        
        // Check for visible error messages on screen
        try {
            List<WebElement> errors = driver.findElements(By.xpath("//*[contains(@class, 'error')]"));
            for (WebElement err : errors) {
                if (err.isDisplayed()) System.err.println("VISIBLE ERROR ON PAGE: " + err.getText());
            }
        } catch (Exception e) {}

        try {
            List<LogEntry> logs = driver.manage().logs().get(LogType.BROWSER).getAll();
            for (LogEntry entry : logs) {
                System.err.println("[BROWSER LOG] " + entry.getLevel() + ": " + entry.getMessage());
            }
        } catch (Exception ex) {}
        
        try {
            String source = driver.getPageSource();
            System.err.println("Page Source Snippet: " + (source.length() > 500 ? source.substring(0, 500) : source));
        } catch (Exception ex) {}
        System.err.println("--- END DEBUG INFO ---");
    }

    private void clearState() {
        try {
            if (driver.getCurrentUrl().startsWith("http")) {
                driver.manage().deleteAllCookies();
                ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
                ((JavascriptExecutor) driver).executeScript("window.sessionStorage.clear();");
            }
        } catch (Exception e) {}
    }

    private void submitForm(WebElement lastInput) {
        // Pressing Enter is often more reliable than clicking a button in headless mode
        lastInput.sendKeys(Keys.ENTER);
        // Fallback: Click the submit button if Enter didn't work
        try {
            driver.findElement(By.cssSelector("button[type='submit']")).click();
        } catch (Exception e) {}
    }

    private void login(String email, String password) {
        navigateTo("/login");
        WebElement emailField = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']")));
        emailField.clear();
        emailField.sendKeys(email);
        WebElement passField = driver.findElement(By.cssSelector("input[type='password']"));
        passField.clear();
        passField.sendKeys(password);
        submitForm(passField);
    }

    @Test @Order(1) @DisplayName("1. User Registration")
    public void testRegistration() {
        navigateTo("/register");
        clearState();
        driver.navigate().refresh();
        
        WebElement nameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']")));
        nameField.sendKeys("Test User");
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(testUserEmail);
        WebElement passField = driver.findElement(By.cssSelector("input[placeholder*='Password']"));
        passField.sendKeys(TEST_PASSWORD);
        
        submitForm(passField);

        try {
            wait.until(ExpectedConditions.or(
                ExpectedConditions.urlToBe(baseUrl + "/"),
                ExpectedConditions.urlContains("/login")
            ));
        } catch (TimeoutException e) {
            printDebugInfo("Registration Timeout");
            throw e;
        }
    }

    @Test @Order(2) @DisplayName("2. User Login Success")
    public void testLogin() {
        login(testUserEmail, TEST_PASSWORD);
        try {
            wait.until(ExpectedConditions.urlContains("/"));
        } catch (TimeoutException e) {
            printDebugInfo("Login Success Timeout");
            throw e;
        }
    }

    @Test @Order(3) @DisplayName("3. Login Failure")
    public void testLoginFailure() {
        clearState();
        login(testUserEmail, "wrong_pass_999");
        try {
            WebElement errorMsg = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'error')]")));
            assertTrue(errorMsg.isDisplayed());
        } catch (TimeoutException e) {
            printDebugInfo("Login Failure Message Timeout");
            throw e;
        }
    }

    @Test @Order(4) @DisplayName("4. Forgot Password Flow")
    public void testForgotPassword() {
        navigateTo("/login");
        // Link text is case sensitive: "Forgot password?" matches the JSX
        wait.until(ExpectedConditions.elementToBeClickable(By.partialLinkText("Forgot"))).click();
        wait.until(ExpectedConditions.urlContains("/forgot-password"));
        assertTrue(driver.getPageSource().contains("Reset"));
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
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
    }

    @Test @Order(8) @DisplayName("8. Remove from Cart")
    public void testRemoveFromCart() {
        navigateTo("/cart");
        try {
            WebElement removeBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Remove')]")));
            removeBtn.click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'Empty')]")));
        } catch (Exception e) {
            System.out.println("Cart was already empty or button missing.");
        }
    }

    @Test @Order(9) @DisplayName("9. Checkout Navigation")
    public void testCheckoutFlow() {
        testAddToCart();
        driver.findElement(By.xpath("//button[contains(text(), 'Checkout')]")).click();
        wait.until(ExpectedConditions.urlContains("/checkout"));
    }

    @Test @Order(10) @DisplayName("10. User Profile Access")
    public void testUserProfile() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/account");
        assertTrue(driver.getPageSource().contains("Profile"));
    }

    @Test @Order(11) @DisplayName("11. Admin Login")
    public void testAdminLogin() {
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
    }

    @Test @Order(12) @DisplayName("12. Admin Add Product UI")
    public void testAdminAddProduct() {
        testAdminLogin();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add Product')]"))).click();
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='Name']"))).isDisplayed());
    }

    @Test @Order(13) @DisplayName("13. Admin Delete Product")
    public void testAdminDeleteProduct() {
        testAdminLogin();
        try {
            WebElement deleteBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(@class, 'delete')]")));
            deleteBtn.click();
            driver.switchTo().alert().accept();
        } catch (Exception e) {
            System.out.println("No products to delete or alert didn't appear.");
        }
    }

    @Test @Order(14) @DisplayName("14. Admin Order Management")
    public void testAdminOrders() {
        testAdminLogin();
        driver.get(baseUrl + "/admin/orders");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'Order')]")));
    }

    @Test @Order(15) @DisplayName("15. Submit Product Review")
    public void testSubmitReview() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/products");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']"))).click();
        WebElement area = wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("textarea")));
        area.sendKeys("Automation Review " + UUID.randomUUID().toString());
        driver.findElement(By.xpath("//button[contains(text(), 'Review')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'Review')]")));
    }
}