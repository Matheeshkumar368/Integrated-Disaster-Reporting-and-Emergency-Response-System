const fs = require('fs');

function cleanFile(filePath, replacements) {
    let content = fs.readFileSync(filePath, 'utf8');
    for (const [target, replacement] of replacements) {
        content = content.replace(target, replacement);
    }
    fs.writeFileSync(filePath, content, 'utf8');
}

// alerts.html
cleanFile('src/main/resources/static/alerts.html', [
    ["if (window.showToast) showToast('Please login to acknowledge alerts', 'warning'); else alert('Please login to acknowledge alerts');", "window.showToast('Please login to acknowledge alerts', 'warning');"],
    ["if (window.showToast) showToast('Alert acknowledged successfully', 'success'); else alert('Alert acknowledged successfully');", "window.showToast('Alert acknowledged successfully', 'success');"],
    ["if (window.showToast) showToast('Failed to acknowledge: ' + error, 'error'); else alert('Failed to acknowledge: ' + error);", "window.showToast('Failed to acknowledge: ' + error, 'error');"],
    ["if (window.showToast) showToast('Network error: ' + error.message, 'error'); else alert('Network error: ' + error.message);", "window.showToast('Network error: ' + error.message, 'error');"]
]);

// home.html
cleanFile('src/main/resources/static/home.html', [
    ["if (window.showToast) showToast('Chat feature coming soon!', 'info'); else alert('Chat feature coming soon!')", "window.showToast('Chat feature coming soon!', 'info')"],
    ["if (window.showToast) showToast('Settings page coming soon!', 'info'); else alert('Settings page coming soon!')", "window.showToast('Settings page coming soon!', 'info')"],
    ["if (window.showToast) showToast('Alert History coming soon!', 'info'); else alert('Alert History coming soon!')", "window.showToast('Alert History coming soon!', 'info')"],
    ["if (window.showToast) showToast('Logged out successfully', 'success'); else alert('Logged out successfully!');", "window.showToast('Logged out successfully', 'success');"]
]);

// index-new-login.html
cleanFile('src/main/resources/static/index-new-login.html', [
    ["if (window.showToast) showToast('Registration Successful! Please login.', 'success'); else alert('Registration Successful! Please login.');", "window.showToast('Registration Successful! Please login.', 'success');"],
    ["if (window.showToast) showToast(response.message || 'Registration Failed', 'error'); else alert(response.message || 'Registration Failed');", "window.showToast(response.message || 'Registration Failed', 'error');"],
    ["if (window.showToast) showToast('Backend connection error', 'error'); else alert('Backend connection error');", "window.showToast('Backend connection error', 'error');"],
    ["if (window.showToast) showToast(`Access Denied: You selected ${selectedRole}, but your account is ${actualRole}.`, 'error'); else alert('Access Denied');", "window.showToast(`Access Denied: You selected ${selectedRole}, but your account is ${actualRole}.`, 'error');"],
    ["if (window.showToast) showToast(data.message || 'Login Failed - Invalid credentials', 'error'); else alert(data.message || 'Login Failed');", "window.showToast(data.message || 'Login Failed - Invalid credentials', 'error');"],
    ["if (window.showToast) showToast('Backend connection error - Make sure the server is running', 'error'); else alert('Backend connection error');", "window.showToast('Backend connection error - Make sure the server is running', 'error');"]
]);

// offline.html
cleanFile('src/main/resources/static/offline.html', [
    ["if (window.showToast) showToast('Device is still offline. Please verify Wi-Fi or cellular mobile data connection.', 'warning'); else alert('Device is still offline.');", "window.showToast('Device is still offline. Please verify Wi-Fi or cellular mobile data connection.', 'warning');"]
]);

// pwa-manager.js
cleanFile('src/main/resources/static/pwa-manager.js', [
    ["alert('To install NDMA Command on this browser:\\n1. Click your browser menu (⋮ or ⋯)\\n2. Select \"Install App\" or \"Add to Home Screen\".');", "window.showToast('To install NDMA Command: Click browser menu (⋮ or ⋯) and select \"Install App\" or \"Add to Home Screen\".', 'info', 5000);"],
    ["alert('To install NDMA Command on iPhone / iPad:\\n1. Tap the Share button (square with arrow pointing up)\\n2. Scroll down and tap \"Add to Home Screen\"\\n3. Tap \"Add\" at the top right.');", "window.showToast('To install on iOS: Tap Share -> Add to Home Screen -> Add.', 'info', 5000);"],
    ["alert('Browser Push Notifications are not supported by this browser.');", "window.showToast('Browser Push Notifications are not supported by this browser.', 'warning');"],
    ["alert('✓ Emergency notifications are active.');", "window.showToast('✓ Emergency notifications are active.', 'success');"],
    ["alert('Emergency notifications are blocked in your browser settings. To receive flash hazard alerts, enable permissions in site settings.');", "window.showToast('Emergency notifications are blocked in browser settings. Please enable notifications.', 'warning');"]
]);

// reports.html
cleanFile('src/main/resources/static/reports.html', [
    ["alert('No telemetry records available to export.');", "window.showToast('No telemetry records available to export.', 'info');"]
]);

// admin-tasks.html
cleanFile('src/main/resources/static/admin-tasks.html', [
    ["confirmed = confirm('Are you sure you want to delete this task?');", "confirmed = false;"]
]);

// profile.html
cleanFile('src/main/resources/static/profile.html', [
    ["confirmed = confirm('Are you sure you want to logout?');", "confirmed = false;"]
]);

// responder-profile.html
cleanFile('src/main/resources/static/responder-profile.html', [
    ["confirmed = confirm('Are you sure you want to logout?');", "confirmed = false;"]
]);

// responder-dashboard.html
cleanFile('src/main/resources/static/responder-dashboard.html', [
    ["notes = prompt(`Transition Task #${taskId} directly to \"${targetStage}\"?\\nAdd SitRep notes (optional):`);", "notes = null;"],
    ["notes = prompt(`Add Field SitRep Note for Task #${taskId}:`);", "notes = null;"]
]);

// admin-profile.html
cleanFile('src/main/resources/static/admin-profile.html', [
    ["pin = prompt('SECURITY CLEARANCE REQUIRED:\\nEnter your 6-digit Admin Security PIN to view the immutable audit trail:');", "pin = null;"]
]);

console.log('Cleaned residual native calls successfully.');

