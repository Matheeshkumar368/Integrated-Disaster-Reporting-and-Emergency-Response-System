const fs = require('fs');
const path = require('path');

// 1. Patch home.html
let home = fs.readFileSync('src/main/resources/static/home.html', 'utf8');
if (!home.includes('toast-manager.js')) {
    home = home.replace(
        '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>',
        '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>\n    <link rel="stylesheet" href="toast-manager.css?v=2" />\n    <script src="toast-manager.js?v=2"></script>'
    );
}
home = home.replace("alert('Chat feature coming soon!')", "if (window.showToast) showToast('Chat feature coming soon!', 'info'); else alert('Chat feature coming soon!')");
home = home.replace("alert('Settings page coming soon!')", "if (window.showToast) showToast('Settings page coming soon!', 'info'); else alert('Settings page coming soon!')");
home = home.replace("alert('Alert History coming soon!')", "if (window.showToast) showToast('Alert History coming soon!', 'info'); else alert('Alert History coming soon!')");
home = home.replace('alert("Logged out successfully!");', "if (window.showToast) showToast('Logged out successfully', 'success'); else alert('Logged out successfully!');");
fs.writeFileSync('src/main/resources/static/home.html', home, 'utf8');
console.log('Patched home.html');

// 2. Patch alerts.html
let alerts = fs.readFileSync('src/main/resources/static/alerts.html', 'utf8');
if (!alerts.includes('toast-manager.js')) {
    alerts = alerts.replace(
        '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>',
        '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>\n    <link rel="stylesheet" href="toast-manager.css?v=2" />\n    <script src="toast-manager.js?v=2"></script>'
    );
}
alerts = alerts.replace("alert('Please login to acknowledge alerts');", "if (window.showToast) showToast('Please login to acknowledge alerts', 'warning'); else alert('Please login to acknowledge alerts');");
alerts = alerts.replace("alert('Alert acknowledged successfully');", "if (window.showToast) showToast('Alert acknowledged successfully', 'success'); else alert('Alert acknowledged successfully');");
alerts = alerts.replace("alert('Failed to acknowledge: ' + error);", "if (window.showToast) showToast('Failed to acknowledge: ' + error, 'error'); else alert('Failed to acknowledge: ' + error);");
alerts = alerts.replace("alert('Network error: ' + error.message);", "if (window.showToast) showToast('Network error: ' + error.message, 'error'); else alert('Network error: ' + error.message);");
fs.writeFileSync('src/main/resources/static/alerts.html', alerts, 'utf8');
console.log('Patched alerts.html');

// 3. Patch index-new-login.html
let newLogin = fs.readFileSync('src/main/resources/static/index-new-login.html', 'utf8');
if (!newLogin.includes('toast-manager.js')) {
    newLogin = newLogin.replace(
        '<link rel="stylesheet" href="role-loading.css?v=2">',
        '<link rel="stylesheet" href="role-loading.css?v=2">\n    <link rel="stylesheet" href="toast-manager.css?v=2">\n    <script src="toast-manager.js?v=2"></script>'
    );
}
newLogin = newLogin.replace('alert("Registration Successful! Please login.");', "if (window.showToast) showToast('Registration Successful! Please login.', 'success'); else alert('Registration Successful! Please login.');");
newLogin = newLogin.replace('alert(response.message || "Registration Failed");', "if (window.showToast) showToast(response.message || 'Registration Failed', 'error'); else alert(response.message || 'Registration Failed');");
newLogin = newLogin.replace('alert("Backend connection error");', "if (window.showToast) showToast('Backend connection error', 'error'); else alert('Backend connection error');");
newLogin = newLogin.replace(
    'alert(`Access Denied!\\n\\nYou selected: ${selectedRole}\\nBut your account is: ${actualRole}\\n\\nPlease go back and select the correct role.`);',
    "if (window.showToast) showToast(`Access Denied: You selected ${selectedRole}, but your account is ${actualRole}.`, 'error'); else alert('Access Denied');"
);
newLogin = newLogin.replace('alert((data.message || "Login Failed - Invalid credentials"));', "if (window.showToast) showToast(data.message || 'Login Failed - Invalid credentials', 'error'); else alert(data.message || 'Login Failed');");
newLogin = newLogin.replace('alert("Backend connection error - Make sure the server is running");', "if (window.showToast) showToast('Backend connection error - Make sure the server is running', 'error'); else alert('Backend connection error');");
fs.writeFileSync('src/main/resources/static/index-new-login.html', newLogin, 'utf8');
console.log('Patched index-new-login.html');

// 4. Patch index.html
let index = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
if (!index.includes('toast-manager.js')) {
    index = index.replace(
        '<link rel="stylesheet" href="command-center.css?v=3" />',
        '<link rel="stylesheet" href="toast-manager.css?v=2" />\n    <script src="toast-manager.js?v=2"></script>\n    <link rel="stylesheet" href="command-center.css?v=3" />'
    );
}
index = index.replace("alert('Registration failed: ' + (resData.message || 'Unknown error'));", "showAlert('Registration failed: ' + (resData.message || 'Unknown error'), 'error');");
index = index.replace("alert('Connection error: ' + err.message);", "showAlert('Connection error: ' + err.message, 'error');");
// Enhance showAlert to also trigger toast
if (!index.includes('window.showToast(msg, type)')) {
    index = index.replace(
        "alertBox.style.display = 'flex';",
        "alertBox.style.display = 'flex';\n            if (window.showToast) window.showToast(msg, type === 'error' ? 'error' : 'success');"
    );
}
fs.writeFileSync('src/main/resources/static/index.html', index, 'utf8');
console.log('Patched index.html');

// 5. Patch offline.html
let offline = fs.readFileSync('src/main/resources/static/offline.html', 'utf8');
if (!offline.includes('toast-manager.js')) {
    offline = offline.replace(
        '<link rel="stylesheet" href="command-center.css?v=3"/>',
        '<link rel="stylesheet" href="command-center.css?v=3"/>\n    <link rel="stylesheet" href="toast-manager.css?v=2"/>\n    <script src="toast-manager.js?v=2"></script>'
    );
}
offline = offline.replace(
    "alert('Device is still offline. Please verify Wi-Fi or cellular mobile data connection.');",
    "if (window.showToast) showToast('Device is still offline. Please verify Wi-Fi or cellular mobile data connection.', 'warning'); else alert('Device is still offline.');"
);
fs.writeFileSync('src/main/resources/static/offline.html', offline, 'utf8');
console.log('Patched offline.html');

// 6. Patch training.html
let training = fs.readFileSync('src/main/resources/static/training.html', 'utf8');
if (!training.includes('toast-manager.js')) {
    training = training.replace(
        '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>',
        '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>\n    <link rel="stylesheet" href="toast-manager.css?v=2" />\n    <script src="toast-manager.js?v=2"></script>'
    );
}
training = training.replace(/alert\('Opening map for Government School Shelter\.\.\.'\); return false;/g, "if (window.showToast) showToast('Navigating to Government School Shelter safe zone map...', 'info'); return false;");
training = training.replace(/alert\('Opening map for Community Hall Shelter\.\.\.'\); return false;/g, "if (window.showToast) showToast('Navigating to Community Hall Shelter safe zone map...', 'info'); return false;");
training = training.replace(/alert\('Opening map for District Hospital Safe Zone\.\.\.'\); return false;/g, "if (window.showToast) showToast('Navigating to District Hospital Safe Zone map...', 'info'); return false;");
training = training.replace(/alert\('Opening map for Flood Relief Center\.\.\.'\); return false;/g, "if (window.showToast) showToast('Navigating to Flood Relief Center map...', 'info'); return false;");
fs.writeFileSync('src/main/resources/static/training.html', training, 'utf8');
console.log('Patched training.html');

