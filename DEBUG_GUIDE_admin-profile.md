    # Debug Guide: admin-profile.html Perpetual Loading Spinner

## Environment & Stack

| Component | Details |
|-----------|---------|
| **Backend** | Spring Boot 4.0.4 / Java 21 |
| **Frontend** | Vanilla JavaScript SPA (static HTML files) |
| **Authentication** | JWT stored in localStorage (`token` key) |
| **Routing** | Multi-page approach with role-based loading screen |
| **Server** | Maven: `./mvnw spring-boot:run` (port 8080) |
| **Database** | MySQL on localhost:3306 |

---

## Root Cause Analysis

### Identified Issues

1. **Stale Loading Screen State**: The `role-loading.js` was caching the loading container from previous page navigations, causing the spinner to persist indefinitely on new pages.

2. **Missing Safety Timeout**: No fallback existed to force-hide the loading screen if something went wrong.

3. **Auth vs Loading Race Condition**: The auth check was running before the loading screen properly initialized, causing brief flashes of login page.

4. **HTTP Method Mismatch**: The profile update used `PUT` but backend expects `POST`.

---

## Fixes Applied

### 1. role-loading.js - Stale Container Cleanup
```javascript
// Now always recreates container on page load
function createElements(forceRecreate) {
    forceRecreate = true;  // Always recreate
    
    // Remove any stale container from previous page
    const existingContainer = document.getElementById('role-loading-screen');
    if (existingContainer && document.body.contains(existingContainer)) {
        existingContainer.remove();
    }
    // ...
}
```

### 2. role-loading.js - Safety Fallback (8 seconds max)
```javascript
// Added safety timeout
setTimeout(function() {
    if (isVisible) {
        console.warn('[RoleLoadingScreen] Safety fallback: Force hiding after timeout');
        cleanup();
        if (container) {
            container.classList.remove('visible');
            container.classList.add('hidden');
            isVisible = false;
        }
    }
}, 8000);
```

### 3. admin-profile.html - Proper Initialization Order
```javascript
// Now initializes loading screen first, then auth check
window.addEventListener('DOMContentLoaded', function() {
    setTimeout(function() {
        if (typeof RoleLoadingScreen !== 'undefined') {
            RoleLoadingScreen.initOnLoad('Admin');
        }
    }, 50);
});

// Auth runs after loading screen has a chance to initialize
```

### 4. admin-profile.html - Fixed HTTP Method
```javascript
// Changed from PUT to POST to match backend
fetch('/api/auth/profile', {
    method: 'POST',  // Fixed
    // ...
});
```

---

## Reproduction Steps

1. Start the backend: `./mvnw spring-boot:run`
2. Open browser to http://localhost:8080
3. Login as Admin (credentials from CORRECT_LOGIN_CREDENTIALS.md)
4. Navigate to Profile page (bottom nav or directly to admin-profile.html)
5. Observe: Should load within 3-8 seconds max (loading screen shows briefly then hides)

---

## Diagnostic Steps

### Browser DevTools

**1. Console Tab - Look for:**
```
[Auth] No token found, redirecting to login      // Token missing
[Auth] User role is not Admin: Responder         // Wrong role
[RoleLoadingScreen] Login redirect detected      // Login working
[RoleLoadingScreen] Safety fallback             // Fallback triggered
```

**2. Network Tab - Check:**
- Request to `/api/auth/profile` → 200 OK expected
- Check Response for proper JSON structure

**3. Application Tab - localStorage:**
- `token` - JWT token exists
- `userRole` - "Admin" string

### Server Logs

Check console for:
```
===== Fetch finished. Check above for Saved or Error messages =====
```

---

## Common Causes & Solutions

| Cause | Symptom | Solution |
|-------|---------|----------|
| Invalid/expired token | Redirects to login | Re-login |
| Wrong role in token | "Access Denied" alert | Login with correct role |
| Missing localStorage | Login screen flash | Check login flow sets token |
| Server down | "Backend connection error" | Restart `./mvnw spring-boot:run` |
| CORS blocking | Console errors | Check SecurityConfig allows `/**` |
| SessionStorage flag lost | Loading shows every page | Check `justLoggedIn` flag |

---

## Validation Commands

```bash
# Test API is running
curl http://localhost:8080/

# Test profile endpoint (needs valid token)
curl -X POST http://localhost:8080/api/auth/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -d '{"name":"Test"}'
```

---

## To Rebuild & Test

```bash
cd M:\demo

# Rebuild
./mvnw clean package -DskipTests

# Run (in separate terminal)
./mvnw spring-boot:run

# Then in browser:
# 1. Go to http://localhost:8080
# 2. Login as Admin
# 3. Navigate to Profile
```

---

## What Data to Provide If Issues Persist

1. **Browser Console Screenshot** - Full console output after page load
2. **Network Tab Screenshot** - All requests during page load
3. **localStorage Screenshot** - Show token and userRole values
4. **Server Console Output** - Last 50 lines after making the request