const fs = require('fs');
let content = fs.readFileSync('src/main/resources/static/training.html', 'utf8');

content = content.replace(
    /onclick="if \(window\.showToast\) showToast\('Navigating to Government School Shelter safe zone map\.\.\.', 'info'\); return false;"/g,
    'href="/citizen-dashboard.html?tab=radar"'
);
content = content.replace(
    /onclick="if \(window\.showToast\) showToast\('Navigating to Community Hall Shelter safe zone map\.\.\.', 'info'\); return false;"/g,
    'href="/citizen-dashboard.html?tab=radar"'
);
content = content.replace(
    /onclick="if \(window\.showToast\) showToast\('Navigating to District Hospital Safe Zone map\.\.\.', 'info'\); return false;"/g,
    'href="/citizen-dashboard.html?tab=radar"'
);
content = content.replace(
    /onclick="if \(window\.showToast\) showToast\('Navigating to Flood Relief Center map\.\.\.', 'info'\); return false;"/g,
    'href="/citizen-dashboard.html?tab=radar"'
);

fs.writeFileSync('src/main/resources/static/training.html', content, 'utf8');
console.log('Successfully updated training.html shelter buttons to real map route');

