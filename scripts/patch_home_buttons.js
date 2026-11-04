const fs = require('fs');
let content = fs.readFileSync('src/main/resources/static/home.html', 'utf8');

content = content.replace("onclick=\"window.location.href='citizen-dashboard.html'\"", "onclick=\"window.location.href='/citizen-dashboard.html?tab=radar'\"");
content = content.replace("onclick=\"window.showToast('Chat feature coming soon!', 'info')\"", "onclick=\"window.location.href='/citizen-dashboard.html?tab=my-reports'\"");
content = content.replace("onclick=\"window.showToast('Settings page coming soon!', 'info')\"", "onclick=\"window.location.href='/profile.html'\"");
content = content.replace("onclick=\"window.showToast('Alert History coming soon!', 'info')\"", "onclick=\"window.location.href='/citizen-dashboard.html?tab=alerts'\"");

fs.writeFileSync('src/main/resources/static/home.html', content, 'utf8');
console.log('Successfully updated home.html buttons to real functioning routes');

