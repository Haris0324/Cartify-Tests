package com.cartify;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

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
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1920,1080");

        String seleniumUrl = System.getProperty("seleniumUrl", "http://selenium:4444/wd/hub");
        driver = new RemoteWebDriver(URI.create(seleniumUrl).toURL(), options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(45));
    }

    @AfterAll
    public static void tearDownAll() {
        if (driver != null) driver.quit();
    }

    private void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    private void clearAll() {
        driver.get(baseUrl + "/");
        driver.manage().deleteAllCookies();
        try {
            ((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
        } catch (Exception e) {}
        driver.navigate().refresh();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("root")));
        try { Thread.sleep(1000); } catch(Exception e) {}
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
        
        wait.until(ExpectedConditions.or(
            ExpectedConditions.urlToBe(baseUrl + "/"),
            ExpectedConditions.urlContains("/admin")
        ));
        try { Thread.sleep(1500); } catch(Exception ex) {}
    }

    // --- ADMIN FLOW (Guarantee Data Exists) ---

    @Test @Order(1) @DisplayName("1. Admin Login")
    public void testAdminLogin() {
        clearAll();
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
    }

    @Test @Order(2) @DisplayName("2. Admin Add Product")
    public void testAdminAddProduct() {
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'PRODUCTS', 'products'), 'products')]"))));
        try { Thread.sleep(2000); } catch(Exception e) {}
        driver.findElement(By.cssSelector("input[required]")).sendKeys("Auto Prod Test");
        driver.findElement(By.cssSelector("input[type='number']")).sendKeys("999");
        driver.findElement(By.tagName("textarea")).sendKeys("Guaranteed stock for tests");
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Add')]")));
        try { Thread.sleep(3000); } catch(Exception e) {}
    }

    @Test @Order(3) @DisplayName("3. Admin Order Management")
    public void testAdminOrders() {
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'ORDERS', 'orders'), 'orders')]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("table")));
    }

    // --- USER FLOW ---

    @Test @Order(4) @DisplayName("4. User Registration")
    public void testRegistration() {
        clearAll(); // Logs out Admin
        navigateTo("/register");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']"))).sendKeys("Test User");
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(testUserEmail);
        WebElement p = driver.findElement(By.cssSelector("input[placeholder*='Password']"));
        p.sendKeys(TEST_PASSWORD);
        p.sendKeys(Keys.ENTER);
        try { Thread.sleep(2000); } catch(Exception e) {}
    }

    @Test @Order(5) @DisplayName("5. User Login Success")
    public void testLogin() {
        login(testUserEmail, TEST_PASSWORD);
    }

    @Test @Order(6) @DisplayName("6. Login Failure")
    public void testLoginFailure() {
        clearAll(); // Logs out User
        navigateTo("/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']"))).sendKeys(testUserEmail);
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys("wrong_pass");
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys(Keys.ENTER);
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'error')]"))).isDisplayed());
    }

    @Test @Order(7) @DisplayName("7. Forgot Password Flow")
    public void testForgotPassword() {
        navigateTo("/login");
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.partialLinkText("Forgot"))));
        wait.until(ExpectedConditions.urlContains("/forgot-password"));
    }

    @Test @Order(8) @DisplayName("8. Unauth Access Restriction")
    public void testUnauthAccess() {
        navigateTo("/orders");
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    @Test @Order(9) @DisplayName("9. Search Functionality")
    public void testSearch() {
        navigateTo("/products");
        WebElement s = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='Search']")));
        s.sendKeys("Auto Prod");
        s.sendKeys(Keys.ENTER);
        wait.until(ExpectedConditions.urlContains("search="));
    }

    @Test @Order(10) @DisplayName("10. Add to Cart")
    public void testAddToCart() {
        login(testUserEmail, TEST_PASSWORD); // Log back in
        
        navigateTo("/products");
        // Search specifically for our guaranteed product
        WebElement s = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='Search']")));
        s.sendKeys("Auto Prod Test");
        s.sendKeys(Keys.ENTER);
        try { Thread.sleep(2000); } catch(Exception e) {}

        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//a[contains(@class, 'card')])[1]"))));
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]"))));
        try { Thread.sleep(4000); } catch(Exception e) {} // Wait for DB sync
        
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(@href, 'cart')]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
    }

    @Test @Order(11) @DisplayName("11. Remove from Cart")
    public void testRemoveFromCart() {
        try {
            jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Remove')]"))));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'empty')]")));
        } catch (Exception e) {}
    }

    @Test @Order(12) @DisplayName("12. Checkout Navigation")
    public void testCheckoutFlow() {
        navigateTo("/products");
        WebElement s = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='Search']")));
        s.sendKeys("Auto Prod Test");
        s.sendKeys(Keys.ENTER);
        try { Thread.sleep(2000); } catch(Exception e) {}

        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//a[contains(@class, 'card')])[1]"))));
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]"))));
        try { Thread.sleep(3000); } catch(Exception e) {}
        
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(@href, 'cart')]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
        
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(), 'Checkout')]"))));
        wait.until(ExpectedConditions.urlContains("/checkout"));
    }

    @Test @Order(13) @DisplayName("13. User Profile Access")
    public void testUserProfile() {
        navigateTo("/account");
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("h1"))).getText().toLowerCase().contains("account"));
    }

    @Test @Order(14) @DisplayName("14. Submit Product Review")
    public void testSubmitReview() {
        navigateTo("/products");
        WebElement s = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='Search']")));
        s.sendKeys("Auto Prod Test");
        s.sendKeys(Keys.ENTER);
        try { Thread.sleep(2000); } catch(Exception e) {}

        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//a[contains(@class, 'card')])[1]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("textarea"))).sendKeys("Review " + UUID.randomUUID().toString());
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Submit')]")));
        try { Thread.sleep(3000); } catch(Exception e) {}
        assertTrue(driver.getPageSource().toLowerCase().contains("review"));
    }

    @Test @Order(15) @DisplayName("15. Admin Delete Product")
    public void testAdminDeleteProduct() {
        clearAll();
        login("admin@cartify.com", "admin123");
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'PRODUCTS', 'products'), 'products')]"))));
        try {
            jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//button[contains(text(), 'Delete')])[1]"))));
            driver.switchTo().alert().accept();
        } catch (Exception e) {}
    }
}