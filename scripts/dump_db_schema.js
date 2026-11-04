const { execSync } = require('child_process');
const fs = require('fs');

const mysqlPath = process.env.MYSQL_EXE || 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe';
const dbPass = process.env.DB_PASSWORD ? `-p${process.env.DB_PASSWORD}` : '';
const query = "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='disaster_management' ORDER BY TABLE_NAME, ORDINAL_POSITION;";

try {
    const output = execSync(`"${mysqlPath}" -u root ${dbPass} -e "${query}"`).toString();
    fs.writeFileSync('scripts/db_schema_dump.txt', output, 'utf8');
    console.log('Saved schema dump to scripts/db_schema_dump.txt');
    console.log('Sample rows:');
    console.log(output.split('\n').slice(0, 30).join('\n'));
} catch (err) {
    console.error('MySQL dump error:', err.message);
}

