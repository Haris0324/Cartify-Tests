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
        // Essential: Use the container name 'frontend' defined in docker-compose
        baseUrl = System.getProperty("baseUrl", "http://frontend:5173");
        testUserEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        ChromeOptions options = new ChromeOptions();
        // Standalone selenium doesn't need --headless here as it's already a headless-ready environment,
        // but we keep it for consistency.
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        String seleniumUrl = System.getProperty("seleniumUrl", "http://selenium:4444/wd/hub");
        System.out.println("Connecting to Selenium at: " + seleniumUrl);
        System.out.println("Testing against Base URL: " + baseUrl);

        try {
            URI seleniumUri = URI.create(seleniumUrl);
            driver = new RemoteWebDriver(seleniumUri.toURL(), options);
        } catch (Exception e) {
            System.err.println("Could not connect to Selenium Grid! Check if the container is running.");
            throw e;
        }

        // Increased timeout for CI environments
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        // Global implicit wait as a fallback
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @AfterAll
    public static void tearDownAll() {
        if (driver != null) {
            driver.quit();
        }
    }

    /**
     * Helper to navigate and wait for the React route to be ready.
     * We wait for the URL AND a common element like the Navbar or Body to ensure render.
     */
    private void navigateTo(String path) {
        driver.get(baseUrl + path);
        // Wait for URL
        wait.until(ExpectedConditions.urlContains(path));
        // Wait for the app root or navbar to ensure the React app has rendered
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("nav")));
    }

    private void clearState() {
        driver.manage().deleteAllCookies();
        ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
        ((JavascriptExecutor) driver).executeScript("window.sessionStorage.clear();");
    }

    private void login(String email, String password) {
        navigateTo("/login");
        WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='email']")));
        emailInput.clear();
        emailInput.sendKeys(email);
        
        driver.findElement(By.cssSelector("input[type='password']")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }

    @Test
    @Order(1)
    @DisplayName("1. User Registration Success")
    public void testRegistration() {
        clearState();
        navigateTo("/register");
        
        // Use presenceOfElementLocated which is more reliable than visibility in some Docker environments
        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='Full Name']")));
        nameInput.sendKeys("Test User");
        
        driver.findElement(By.cssSelector("input[placeholder='Email']")).sendKeys(testUserEmail);
        driver.findElement(By.cssSelector("input[placeholder*='Password']")).sendKeys(TEST_PASSWORD);
        
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Wait for redirect to home
        wait.until(ExpectedConditions.urlToBe(baseUrl + "/"));
        assertFalse(driver.getCurrentUrl().contains("/register"));
    }

    @Test
    @Order(2)
    @DisplayName("2. User Logout & Login Success")
    public void testLogin() {
        navigateTo("/");
        try {
            WebElement logoutBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Logout')]")));
            logoutBtn.click();
        } catch (Exception e) { 
            // fallback if button not found
            clearState();
        }

        login(testUserEmail, TEST_PASSWORD);
        wait.until(ExpectedConditions.or(
            ExpectedConditions.urlToBe(baseUrl + "/"),
            ExpectedConditions.urlToBe(baseUrl + "/account")
        ));
        assertFalse(driver.getCurrentUrl().contains("/login"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Login Failure with Wrong Password")
    public void testLoginFailure() {
        clearState();
        login(testUserEmail, "wrongpassword");
        // Look for the error class
        WebElement errorMsg = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'error')]")));
        assertTrue(errorMsg.isDisplayed());
    }

    @Test
    @Order(5)
    @DisplayName("5. Unauthenticated User Access Restriction")
    public void testUnauthAccess() {
        clearState();
        // Go to home first to ensure we are on the domain
        driver.get(baseUrl + "/");
        driver.get(baseUrl + "/orders");
        
        // Verify redirect to login
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("login"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Add to Cart Functionality")
    public void testAddToCart() {
        navigateTo("/products");
        // Ensure products are loaded (look for a product card)
        WebElement firstProduct = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']")));
        firstProduct.click();

        WebElement addBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Add to Cart')]")));
        addBtn.click();

        navigateTo("/cart");
        WebElement cartItem = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class, 'item')]")));
        assertTrue(cartItem.isDisplayed());
    }

    @Test
    @Order(11)
    @DisplayName("11. Admin Side Login")
    public void testAdminLogin() {
        clearState();
        login("admin@cartify.com", "admin123");
        wait.until(ExpectedConditions.urlContains("/admin"));
        assertTrue(driver.getCurrentUrl().contains("/admin"));
    }

    @Test
    @Order(15)
    @DisplayName("15. Product Details - Review Submission")
    public void testSubmitReview() {
        clearState();
        login(testUserEmail, TEST_PASSWORD);
        navigateTo("/products");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href^='/products/']"))).click();

        WebElement reviewArea = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//textarea[contains(@placeholder, 'experience')]")));
        reviewArea.sendKeys("Great product! Testing...");
        driver.findElement(By.xpath("//button[text()='Submit Review']")).click();

        WebElement reviewText = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//p[text()='Great product! Testing...']")));
        assertTrue(reviewText.isDisplayed());
    }
}