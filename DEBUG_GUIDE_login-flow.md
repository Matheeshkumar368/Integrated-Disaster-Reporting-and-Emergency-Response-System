# Debug Guide: Login Flow - Perpetual Loading & Redirect Loop

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

1. **Session Storage Flag Race Condition**: The `justLoggedIn` flag in sessionStorage is set AFTER the loading screen starts its animation, but it's consumed too quickly on the destination page. The flag may get consumed before auth validation completes.

2. **Token Validation Sync vs Async Mismatch**: The auth check runs synchronously but some validations (like API calls) might fail silently, causing the code to fall through to redirect.

3. **No API Token Validation**: Currently the code only parses JWT locally (`atob()`) but never validates the token with the backend. If the backend rejects the token (expired/invalid), the user gets stuck.

4. **Missing 401 Handling**: When the backend returns 401 (Unauthorized), there's no clear mechanism to redirect back to login - the code just stays on the loading screen.

5. **Role from JWT vs Server Mismatch**: Login stores role from server response in localStorage (`userRole`), but auth check reads role from JWT payload - potential mismatch if JWT doesn't contain the correct role.

---

## Reproduction Steps

1. **Start the backend**:
   ```bash
   cd M:\demo
   ./mvnw spring-boot:run
   ```

2. **Open browser** to `http://localhost:8080`

3. **Login as Admin** with valid credentials (from `CORRECT_LOGIN_CREDENTIALS.md` if exists)

4. **Observe**: 
   - Loading screen appears (3 seconds)
   - Navigate to dashboard
   - Loading screen may appear again briefly OR
   - User gets redirected back to login screen

5. **Check browser console** (F12):
   ```
   [RoleLoadingScreen] Login redirect detected, skipping loading screen
   [Auth] No token found, redirecting to login    <- IF THIS HAPPENS, BUG EXISTS
   ```

---

## Diagnostic Steps

### 1. Browser Console - Look for these patterns:

| Console Output | Meaning | Action Needed |
|---------------|---------|---------------|
| `[Auth] No token found, redirecting to login` | localStorage token missing | Check login stores token properly |
| `[Auth] User role is not Admin: X` | Role mismatch in JWT | Check JWT generation includes role |
| `[RoleLoadingScreen] Safety fallback` | Loading didn't hide | Check init flow |
| `JWT decode error` | Malformed token | Check token format |

### 2. Network Tab:

- Check if `/api/auth/profile` returns 401
- Look for any failed API calls

### 3. localStorage (Application Tab):

| Key | Expected Value |
|-----|----------------|
| `token` | JWT string starting with `eyJ...` |
| `userRole` | `"Admin"`, `"Responder"`, or `"Citizen"` |
| `justLoggedIn` | Should NOT exist after login completes |

---

## Fixes Applied

### 1. Fix: Add API Token Validation

Add backend validation in dashboard pages to verify token is still valid:

```javascript
// Add to admin-dashboard.html and other protected pages
async function validateTokenWithBackend(token) {
    try {
        const response = await fetch('/api/auth/profile', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        return response.ok;
    } catch (e) {
        console.error('Token validation failed:', e);
        return false;
    }
}
```

### 2. Fix: Improve Auth Check with Better Error Handling

Replace synchronous JWT parsing with async validation:

```javascript
// In admin-dashboard.html - lines ~470-490
(async function() {
    const token = localStorage.getItem('token');
    const userRoleFromStorage = localStorage.getItem('userRole');
    
    if (!token) {
        console.log('[Auth] No token found, redirecting to login');
        RoleLoadingScreen.hide();
        window.location.href = '/index.html';
        return;
    }
    
    // First verify token with backend
    const isValid = await validateTokenWithBackend(token);
    if (!isValid) {
        console.log('[Auth] Token rejected by server, clearing and redirecting');
        localStorage.removeItem('token');
        localStorage.removeItem('userRole');
        RoleLoadingScreen.hide();
        window.location.href = '/index.html';
        return;
    }
    
    // Then check role from localStorage (more reliable than JWT parsing)
    if (userRoleFromStorage !== 'Admin') {
        console.log('[Auth] User role is not Admin:', userRoleFromStorage);
        alert('Access Denied: Only Admin can access this page');
        RoleLoadingScreen.hide();
        window.location.href = '/citizen-dashboard.html';
        return;
    }
    
    console.log('[Auth] Admin authenticated successfully');
    RoleLoadingScreen.hide();
    
})();
```

### 3. Fix: Ensure Token Set Before Navigation

In `index.html` login function - ensure localStorage is definitely set:

```javascript
// Around line 372-390 in index.html
if (data.status === "success" && data.token) {
    const actualRole = data.role || 'Citizen';
    
    // CRITICAL: Store token FIRST, then verify
    localStorage.setItem("token", data.token);
    localStorage.setItem("userRole", actualRole);
    
    // Double-check localStorage was set
    if (!localStorage.getItem('token')) {
        alert('Failed to save session. Please try again.');
        return;
    }
    
    // Now navigate
    setTimeout(function() {
        let targetUrl;
        if (actualRole === 'Admin') {
            targetUrl = "admin-dashboard.html";
        } else if (actualRole === 'Responder') {
            targetUrl = "responder-dashboard.html";
        } else {
            targetUrl = "citizen-dashboard.html";
        }
        RoleLoadingScreen.showAndNavigate(actualRole, targetUrl);
    }, 50);
}
```

### 4. Fix: Increase Safety Timeout in role-loading.js

The safety fallback should be shorter to prevent long waits:

```javascript
// Around line 469-481 in role-loading.js
// SAFETY FALLBACK: Always remove loading screen after max 5 seconds
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
}, 5000); // Reduced from 8000 to 5000
```

---

## Common Causes & Solutions Table

| Cause | Symptom | Solution |
|-------|---------|----------|
| Invalid/expired token | Redirects to login | Re-login; check token expiry in JWT |
| Wrong role in JWT | "Access Denied" alert | Ensure JWT contains correct role claim |
| localStorage cleared | "No token found" | Check for private/incognito mode |
| Server down | Loading hangs forever | Restart `./mvnw spring-boot:run` |
| CORS blocking | Console errors | Check SecurityConfig allows `/**` |
| SessionStorage cleared | Loading shows every page | Ensure `justLoggedIn` flag works |

---

## Validation Commands

```bash
# Test API is running
curl http://localhost:8080/

# Test login endpoint
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"password123"}'

# Test profile endpoint (needs valid token - replace YOUR_TOKEN)
curl -X POST http://localhost:8080/api/auth/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{}'
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
# 3. Navigate to Profile or other pages
# 4. Check console for [Auth] messages
```

---

## What Data to Provide If Issues Persist

1. **Browser Console Screenshot** - Full console output after login
2. **Network Tab Screenshot** - All requests during login flow
3. **localStorage Screenshot** - Show token and userRole values
4. **Server Console Output** - Last 50 lines after login attempt