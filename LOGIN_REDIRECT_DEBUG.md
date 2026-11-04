# DEBUGGING GUIDE: Login Redirect Issue

## Issue Description

**Symptoms:**
1. User clicks Login → Loading screen runs correctly (3 seconds)
2. After loading finishes → Login page remains visible
3. App navigates to a SECOND site instead of the dashboard

**Expected Behavior:** After successful login, navigate to the authenticated home page (e.g., `admin-dashboard.html`).

---

## Environment Details

| Component | Details |
|-----------|---------|
| **Backend** | Spring Boot 4.0.4 / Java 21 (port 8080) |
| **Frontend** | Vanilla JavaScript SPA (static HTML) |
| **Auth Method** | Custom JWT in localStorage |
| **API Base** | `http://localhost:8080/api/auth/*` |
| **Pages** | index.html → role-loading.js → dashboard pages |

---

## Reproduction Steps

1. Open browser to `http://localhost:8080/`
2. Select a role (e.g., Admin)
3. Enter valid credentials and click Login
4. Observe loading screen (3s animation)
5. **EXPECTED:** Dashboard page loads
6. **ACTUAL:** Login page remains visible, then navigates elsewhere

---

## Root Cause Analysis

### Code Flow

```
index.html (login)
  → login() function (line 338-400)
    → fetch('/api/auth/login') 
    → localStorage.setItem('token', data.token)
    → RoleLoadingScreen.showAndNavigate(role, targetUrl)
    
role-loading.js (showAndNavigate, line 416-436)
  → setLoginRedirectFlag() 
  → showWithSequence(role) → 3 second animation
  → verifyResourcesReady()
  → window.location.href = targetUrl
```

### Potential Issues Identified

| Issue | Location | Evidence |
|-------|----------|----------|
| **1. Loading screen not hiding** | role-loading.js:461-467 | `showWithSequence` runs but may not call `hide()` properly |
| **2. Race condition in initOnLoad** | role-loading.js:443-482 | Dashboard calls `initOnLoad` which also shows loading, fighting with hide() |
| **3. Login redirect flag conflict** | role-loading.js:46-59, 454-458 | Both login flow AND dashboard init read same `justLoggedIn` flag |
| **4. Navigation timing** | index.html:376-389 | setTimeout 50ms may not be enough for localStorage to persist |

---

## Diagnostic Steps

### 1. Browser Console - Check for errors

Open DevTools (F12) → Console tab:
- Look for: `[RoleLoadingScreen]` log messages
- Check for JavaScript errors (red)
- Note any warnings about localStorage or navigation

### 2. Network Tab - Verify requests

In DevTools → Network tab:
- Filter by `fetch` or `XHR`
- **Request 1:** POST `/api/auth/login` → Should return 200 with token
- **Request 2:** GET `/admin-dashboard.html` → Should return 200

### 3. Application Tab - Check localStorage

In DevTools → Application tab → localStorage:
- Key: `token` → Should contain JWT string
- Key: `userRole` → Should be "Admin" (or role)
- Key: `justLoggedIn` → Should be removed after redirect

### 4. Check redirect chain

In DevTools → Network tab:
- Click on the dashboard request
- Check "Initiator" column to see what triggered it
- Check "Response" tab for actual redirect location

---

## Console Log Analysis

Add this debug code to trace the issue:

```javascript
// Add to index.html before login() call
console.log('[DEBUG] Starting login flow');
console.log('[DEBUG] Token stored:', !!localStorage.getItem('token'));
console.log('[DEBUG] Role stored:', localStorage.getItem('userRole'));

// Add to role-loading.js showAndNavigate
console.log('[DEBUG] showAndNavigate called with:', role, targetUrl);

// Add to role-loading.js initOnLoad  
console.log('[DEBUG] initOnLoad called, checking flag:', checkLoginRedirect());
```

---

## Code References

### index.html - Login Handler (lines 337-400)

```javascript
function login() {
    // ... fetch login ...
    
    if (data.status === "success" && data.token) {
        localStorage.setItem("token", data.token);
        localStorage.setItem("userRole", actualRole);
        
        setTimeout(function() {
            let targetUrl;
            if (actualRole === 'Admin') targetUrl = "admin-dashboard.html";
            else if (actualRole === 'Responder') targetUrl = "responder-dashboard.html";
            else targetUrl = "citizen-dashboard.html";
            
            RoleLoadingScreen.showAndNavigate(actualRole, targetUrl);
        }, 50);
    }
}
```

### role-loading.js - showAndNavigate (lines 416-436)

```javascript
function showAndNavigate(role, targetUrl) {
    // Set flag for login redirect
    setLoginRedirectFlag();  // Sets sessionStorage 'justLoggedIn'
    
    showWithSequence(role).then(function() {
        verifyResourcesReady(function() {
            if (targetUrl) {
                window.location.href = targetUrl;  // THE REDIRECT
            }
        });
    });
}
```

### role-loading.js - initOnLoad (lines 443-482)

```javascript
function initOnLoad(role, options) {
    // THIS RUNS ON DASHBOARD PAGE - CONFLICT!
    
    // Skip loading for login redirect (already showed loading during login)
    if (checkLoginRedirect()) {  // Reads same flag!
        console.log('[RoleLoadingScreen] Login redirect detected, skipping loading screen');
        return;
    }
    
    // Show loading immediately
    showWithSequence(role).then(function() {
        verifyResourcesReady(function() {
            hide();  // Hides the loading
        });
    });
}
```

---

## Common Causes & Fixes

### Fix 1: Increase navigation delay

```javascript
// index.html line 376
// Change from:
setTimeout(function() { ... }, 50);

// To:
setTimeout(function() { ... }, 200);
```

### Fix 2: Clear flag after use

```javascript
// Add to role-loading.js after navigation
function showAndNavigate(role, targetUrl) {
    setLoginRedirectFlag();
    
    showWithSequence(role).then(function() {
        verifyResourcesReady(function() {
            // Clear the flag BEFORE navigation
            sessionStorage.removeItem('justLoggedIn');
            
            if (targetUrl) {
                window.location.href = targetUrl;
            }
        });
    });
}
```

### Fix 3: Add console logs to verify flow

```javascript
// Add to index.html login success handler
console.log('[LOGIN] Token saved, triggering navigation');
console.log('[LOGIN] Target URL:', targetUrl);
```

### Fix 4: Check for external redirect

Search entire codebase for unexpected redirects:

```bash
# Search for external domains
grep -r "window.location.href = 'http" src/main/resources/static/
grep -r "window.location.href = \"http" src/main/resources/static/
```

---

## Recent Changes to Check

1. **role-loading.js** - Recent changes added safety timeout and fixed container recreation
2. **index.html** - Login handler unchanged from original
3. **Dashboard pages** - Added `RoleLoadingScreen.initOnLoad()` call

---

## Questions for Further Diagnosis

To narrow down the exact cause, please provide:

1. **Browser console output** - Any errors or `[RoleLoadingScreen]` logs?
2. **Network tab** - Does the redirect request go to the dashboard URL?
3. **localStorage** - Is `token` present after login?
4. **What URL does it redirect to?** - Is it an external site or a different page?

---

## Regression Test Checklist

After applying fixes, verify:

- [ ] Login with valid credentials → Dashboard loads
- [ ] Login with invalid credentials → Error message shown
- [ ] Role mismatch → Proper error shown, stays on login
- [ ] Back/forward browser navigation → Works correctly
- [ ] Refresh dashboard → Stays on dashboard (no redirect to login)
- [ ] Multiple rapid logins → No race conditions
- [ ] Console clean of errors

---

## Quick Fix to Try

Add explicit delay and verify localStorage:

```javascript
// Modified login handler
localStorage.setItem("token", data.token);
localStorage.setItem("userRole", actualRole);

// Verify storage worked
console.log('Token in storage:', localStorage.getItem('token'));

// Increase delay to ensure persistence
setTimeout(function() {
    console.log('Navigating to:', targetUrl);
    window.location.href = targetUrl;
}, 100);  // Increased from 50 to 100
```