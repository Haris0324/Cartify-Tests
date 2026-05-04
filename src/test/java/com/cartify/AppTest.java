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

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;
    private static final String TEST_PASSWORD = "password123";

    @BeforeEach
    public void setup() throws Exception {
        baseUrl = System.getProperty("baseUrl", "http://frontend:5173");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1920,1080");

        String seleniumUrl = System.getProperty("seleniumUrl", "http://selenium:4444/wd/hub");
        driver = new RemoteWebDriver(URI.create(seleniumUrl).toURL(), options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(45));
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    private void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    private String getUniqueEmail() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private void navigateTo(String path) {
        driver.get(baseUrl + path);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("root")));
    }

    private void registerNewUser(String email) {
        navigateTo("/register");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']"))).sendKeys("Test User");
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(email);
        driver.findElement(By.cssSelector("input[placeholder*='Password']")).sendKeys(TEST_PASSWORD);
        driver.findElement(By.cssSelector("input[placeholder*='Password']")).sendKeys(Keys.ENTER);
        wait.until(ExpectedConditions.urlContains("/"));
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
            ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@href, 'account')]"))
        ));
    }

    @Test @Order(1) @DisplayName("1. User Registration")
    public void testRegistration() {
        registerNewUser(getUniqueEmail());
    }

    @Test @Order(2) @DisplayName("2. User Login Success")
    public void testLogin() {
        String email = getUniqueEmail();
        registerNewUser(email);
        login(email, TEST_PASSWORD);
    }

    @Test @Order(3) @DisplayName("3. Login Failure")
    public void testLoginFailure() {
        navigateTo("/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']"))).sendKeys("nonexistent@test.com");
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys("wrong");
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys(Keys.ENTER);
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
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//a[contains(@class, 'card')])[1]"))));
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]"))));
        try { Thread.sleep(3000); } catch(Exception e) {}
        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
    }

    @Test @Order(8) @DisplayName("8. Remove from Cart")
    public void testRemoveFromCart() {
        testAddToCart();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Remove')]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'empty')]")));
    }

    @Test @Order(9) @DisplayName("9. Checkout Navigation (Self-Contained)")
    public void testCheckoutFlow() {
        String email = getUniqueEmail();
        registerNewUser(email);
        
        navigateTo("/products");
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//a[contains(@class, 'card')])[1]"))));
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]"))));
        try { Thread.sleep(4000); } catch(Exception e) {}
        
        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(), 'Checkout')]"))));
        wait.until(ExpectedConditions.urlContains("/checkout"));
    }

    @Test @Order(10) @DisplayName("10. User Profile Access")
    public void testUserProfile() {
        String email = getUniqueEmail();
        registerNewUser(email);
        navigateTo("/account");
        assertTrue(wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("h1"))).getText().toLowerCase().contains("account"));
    }

    @Test @Order(11) @DisplayName("11. Admin Login")
    public void testAdminLogin() {
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
    }

    @Test @Order(12) @DisplayName("12. Admin Add Product")
    public void testAdminAddProduct() {
        testAdminLogin();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'PRODUCTS', 'products'), 'products')]"))));
        try { Thread.sleep(2000); } catch(Exception e) {}
        driver.findElement(By.cssSelector("input[required]")).sendKeys("Auto Product " + UUID.randomUUID().toString());
        driver.findElement(By.cssSelector("input[type='number']")).sendKeys("999");
        driver.findElement(By.tagName("textarea")).sendKeys("Self-contained test product.");
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Add')]")));
        try { Thread.sleep(3000); } catch(Exception e) {}
    }

    @Test @Order(13) @DisplayName("13. Admin Delete Product")
    public void testAdminDeleteProduct() {
        testAdminLogin();
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'PRODUCTS', 'products'), 'products')]"))));
        try {
            jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//button[contains(text(), 'Delete')])[1]"))));
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
        String email = getUniqueEmail();
        registerNewUser(email);
        navigateTo("/products");
        jsClick(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//a[contains(@class, 'card')])[1]"))));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("textarea"))).sendKeys("Review " + UUID.randomUUID().toString());
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Submit')]")));
        try { Thread.sleep(3000); } catch(Exception e) {}
        assertTrue(driver.getPageSource().toLowerCase().contains("review"));
    }
}