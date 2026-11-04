const mysql = require('mysql2/promise');

async function main() {
    try {
        const conn = await mysql.createConnection({
            host: process.env.DB_HOST || 'localhost',
            user: process.env.DB_USERNAME || 'root',
            password: process.env.DB_PASSWORD || '',
            database: process.env.DB_NAME || 'disaster_management'
        });
        const [reports] = await conn.query('SELECT id, title, image_url, status FROM citizen_reports ORDER BY id DESC LIMIT 5');
        console.log('REPORTS:', JSON.stringify(reports, null, 2));

        const [media] = await conn.query('SELECT id, incident_id, incident_type, media_type, original_filename, stored_filename, file_path FROM incident_media ORDER BY id DESC LIMIT 5');
        console.log('MEDIA:', JSON.stringify(media, null, 2));

        const [tasks] = await conn.query('SELECT id, title, status, responder_email, citizen_report_id, acknowledged_at, acknowledged_by, completed_at FROM rescue_tasks ORDER BY id DESC LIMIT 5');
        console.log('TASKS:', JSON.stringify(tasks, null, 2));

        const [users] = await conn.query('SELECT id, email, role, name, is_active FROM users');
        console.log('USERS:', JSON.stringify(users, null, 2));

        await conn.end();
    } catch (e) {
        console.error('DB Error:', e);
    }
}

main();

