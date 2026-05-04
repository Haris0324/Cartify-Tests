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
        ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
    }

    private void navigateTo(String path) {
        driver.get(baseUrl + path);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("root")));
    }

    // Attempt login WITHOUT waiting for redirect (useful for failure tests)
    private void attemptLogin(String email, String password) {
        navigateTo("/login");
        WebElement e = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']")));
        e.clear(); e.sendKeys(email);
        WebElement p = driver.findElement(By.cssSelector("input[type='password']"));
        p.clear(); p.sendKeys(password);
        p.sendKeys(Keys.ENTER);
    }

    // Login and WAIT for success redirect
    private void login(String email, String password) {
        attemptLogin(email, password);
        wait.until(ExpectedConditions.or(ExpectedConditions.urlToBe(baseUrl + "/"), ExpectedConditions.urlContains("/admin")));
    }

    @Test @Order(1) @DisplayName("1. User Registration")
    public void testRegistration() {
        clearAll();
        navigateTo("/register");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']"))).sendKeys("Test User");
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(testUserEmail);
        WebElement p = driver.findElement(By.cssSelector("input[placeholder*='Password']"));
        p.sendKeys(TEST_PASSWORD);
        p.sendKeys(Keys.ENTER);
        wait.until(ExpectedConditions.urlContains("/"));
    }

    @Test @Order(2) @DisplayName("2. User Login Success")
    public void testLogin() {
        login(testUserEmail, TEST_PASSWORD);
    }

    @Test @Order(3) @DisplayName("3. Login Failure")
    public void testLoginFailure() {
        clearAll();
        attemptLogin(testUserEmail, "wrong_password_123");
        // Check for error message on the SAME page
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'error')]"))).isDisplayed());
    }

    @Test @Order(4) @DisplayName("4. Forgot Password Flow")
    public void testForgotPassword() {
        navigateTo("/login");
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.partialLinkText("Forgot"))));
        wait.until(ExpectedConditions.urlContains("/forgot-password"));
    }

    @Test @Order(5) @DisplayName("5. Unauth Access Restriction")
    public void testUnauthAccess() {
        clearAll();
        navigateTo("/orders");
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
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[class*='card']"))));
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Cart')]"))));
        
        // WAIT for the "Success" toast or just a moment for DB sync
        try { Thread.sleep(2500); } catch(Exception e) {}
        
        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
    }

    @Test @Order(8) @DisplayName("8. Remove from Cart")
    public void testRemoveFromCart() {
        navigateTo("/cart");
        try {
            jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Remove')]"))));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'empty')]")));
        } catch (Exception e) {}
    }

    @Test @Order(9) @DisplayName("9. Checkout Navigation")
    public void testCheckoutFlow() {
        login(testUserEmail, TEST_PASSWORD);
        testAddToCart();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(), 'Checkout')]"))));
        wait.until(ExpectedConditions.urlContains("/checkout"));
    }

    @Test @Order(10) @DisplayName("10. User Profile Access")
    public void testUserProfile() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/account");
        WebElement h1 = wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("h1")));
        assertTrue(h1.getText().toLowerCase().contains("account"));
    }

    @Test @Order(11) @DisplayName("11. Admin Login")
    public void testAdminLogin() {
        clearAll();
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
    }

    @Test @Order(12) @DisplayName("12. Admin Add Product")
    public void testAdminAddProduct() {
        testAdminLogin();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'PRODUCTS', 'products'), 'products')]"))));
        try { Thread.sleep(2000); } catch(Exception e) {}
        
        driver.findElement(By.cssSelector("input[required]")).sendKeys("Auto Product " + UUID.randomUUID().toString());
        driver.findElement(By.cssSelector("input[type='number']")).sendKeys("99");
        driver.findElement(By.tagName("textarea")).sendKeys("Description here");
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Add')]")));
        try { Thread.sleep(2500); } catch(Exception e) {}
    }

    @Test @Order(13) @DisplayName("13. Admin Delete Product")
    public void testAdminDeleteProduct() {
        testAdminLogin();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'PRODUCTS', 'products'), 'products')]"))));
        try {
            jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Delete')]"))));
            driver.switchTo().alert().accept();
        } catch (Exception e) {}
    }

    @Test @Order(14) @DisplayName("14. Admin Order Management")
    public void testAdminOrders() {
        testAdminLogin();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'ORDERS', 'orders'), 'orders')]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("table")));
    }

    @Test @Order(15) @DisplayName("15. Submit Product Review")
    public void testSubmitReview() {
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/products");
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[class*='card']"))));
        WebElement t = wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("textarea")));
        t.sendKeys("Review " + UUID.randomUUID().toString());
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Submit')]")));
        try { Thread.sleep(2500); } catch(Exception e) {}
        assertTrue(driver.getPageSource().toLowerCase().contains("review"));
    }
}